package com.sicost.meops;

import com.sicost.common.context.CurrentUserProvider;
import com.sicost.common.persistence.DatabaseMonthCodec;
import com.sicost.common.validation.ProjectInputPolicy;
import java.time.Clock;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.meops.ProfileDtos.*;

@Service
public class ProfileService {
    private final ProfileRepository repository;
    private final CurrentUserProvider users;
    private final Clock clock;
    public ProfileService(ProfileRepository repository,CurrentUserProvider users,Clock clock) {
        this.repository=repository;this.users=users;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Profile getMyProfile() {
        String userId=users.currentUser().userId();
        User user=repository.requiredUser(userId);
        String month=YearMonth.from(ProjectInputPolicy.today(clock)).toString();
        return new Profile(user.userId(),user.userName(),repository.activeRoles(userId),
            repository.parts(userId,DatabaseMonthCodec.toDatabase(month)),month,
            "이름·역할은 관리자, 보유 스킬·거주지는 개발자 풀에서 수정합니다.");
    }
}
