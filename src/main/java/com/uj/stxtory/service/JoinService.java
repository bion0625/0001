package com.uj.stxtory.service;

import com.uj.stxtory.MsgConstants;
import com.uj.stxtory.domain.dto.LoginUser;
import com.uj.stxtory.domain.entity.TbUser;
import com.uj.stxtory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class JoinService {

    @Autowired
    UserRepository userRepository;

    public String join(String userId, String userName, String userPassword,
                        String userEmail, String userPhone){

        String complete = MsgConstants.FAIL;

        Optional<TbUser> byUserId = userRepository.findByUserLoginId(userId);
        if (byUserId.isPresent()){
            complete = MsgConstants.DUPLICATE_ID;
            return complete;
        }

        TbUser tbUser = new TbUser();
        tbUser.setUserLoginId(userId);
        tbUser.setUserName(userName);
        tbUser.setUserPassword(userPassword);
        tbUser.setUserEmail(userEmail);
        tbUser.setUserPhone(userPhone);

        TbUser save = userRepository.save(tbUser);
        if (save != null && save.getId() != null){
            complete = MsgConstants.SUCCESS;
        }

        return complete;
    }
}
