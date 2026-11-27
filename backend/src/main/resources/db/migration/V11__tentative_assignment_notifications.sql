ALTER TABLE tb_hr_dev ADD COLUMN email_addr varchar(100);
COMMENT ON COLUMN tb_hr_dev.email_addr IS '가배정 안내를 받을 개발자의 이메일 주소';

CREATE TABLE tb_sl_tent_mail_outbox (
    mail_id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    dev_id varchar(20) NOT NULL,
    opp_nm varchar(100) NOT NULL,
    event_type varchar(16) NOT NULL CHECK (event_type IN ('ASSIGNED', 'RELEASED', 'PROBABILITY')),
    previous_probability numeric(5,2),
    probability numeric(5,2) NOT NULL,
    delivery varchar(10) NOT NULL DEFAULT 'PENDING'
        CHECK (delivery IN ('PENDING', 'SENDING', 'ACCEPTED', 'FAILED', 'UNKNOWN', 'SKIPPED')),
    queued_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    updated_at timestamptz NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX ix_tb_sl_tent_mail_pending
    ON tb_sl_tent_mail_outbox (mail_id) WHERE delivery = 'PENDING';

CREATE FUNCTION queue_tentative_assignment_mail() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    event varchar(16);
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.tent_stat_cd = 'ACT' THEN
            event := 'ASSIGNED';
        END IF;
    ELSIF OLD.tent_stat_cd = 'ACT' AND NEW.tent_stat_cd = 'REL' THEN
        event := 'RELEASED';
    END IF;

    IF event IS NOT NULL THEN
        INSERT INTO tb_sl_tent_mail_outbox (dev_id, opp_nm, event_type, probability)
        SELECT d.dev_id, o.opp_nm, event, o.win_prbl_rt
        FROM tb_sl_opp o
        JOIN tb_hr_dev d ON d.dev_id = NEW.dev_id
        WHERE o.opp_id = NEW.opp_id AND d.email_addr IS NOT NULL;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER queue_tentative_assignment_mail
AFTER INSERT OR UPDATE ON tb_sl_tent_asgn
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW EXECUTE FUNCTION queue_tentative_assignment_mail();

CREATE FUNCTION queue_tentative_probability_mail() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO tb_sl_tent_mail_outbox (dev_id, opp_nm, event_type, previous_probability, probability)
    SELECT t.dev_id, NEW.opp_nm, 'PROBABILITY', OLD.win_prbl_rt, NEW.win_prbl_rt
    FROM tb_sl_tent_asgn t
    JOIN tb_hr_dev d ON d.dev_id = t.dev_id
    WHERE t.opp_id = NEW.opp_id AND t.tent_stat_cd = 'ACT' AND d.email_addr IS NOT NULL;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER queue_tentative_probability_mail
AFTER UPDATE ON tb_sl_opp
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW WHEN (OLD.win_prbl_rt IS DISTINCT FROM NEW.win_prbl_rt)
EXECUTE FUNCTION queue_tentative_probability_mail();
