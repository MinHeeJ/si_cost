package com.sicost.clientsops;

import com.sicost.common.history.AccessLogWriter;
import com.sicost.common.security.AccountCipher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.sicost.clientsops.ClientActionsDtos.*;

@Service
public class ClientAccountService {
    private final ClientActionsRepository repository;
    private final AccountCipher cipher;
    private final AccessLogWriter access;
    public ClientAccountService(ClientActionsRepository repository, AccountCipher cipher, AccessLogWriter access) {
        this.repository=repository; this.cipher=cipher; this.access=access;
    }
    @Transactional
    public Account view(String id,HttpServletRequest request) {
        var row=repository.client(id,false);
        if (row==null) throw ClientActionsService.missing();
        String number=cipher.decrypt(row.encryptedAccount());
        String bank=row.bankCode()==null?null:repository.bankName(row.bankCode());
        access.append(new AccessLogWriter.Event("ACCT_VIEW","UI-15",id,null,null),request);
        return new Account(id,row.bankCode(),bank,number,row.depositor());
    }
}
