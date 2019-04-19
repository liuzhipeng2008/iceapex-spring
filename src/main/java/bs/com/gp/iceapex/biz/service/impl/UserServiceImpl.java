package bs.com.gp.iceapex.biz.service.impl;

import bs.com.gp.iceapex.biz.service.IUserService;
import bs.com.gp.iceapex.mvc.annotation.GPService;

@GPService("userService")
public class UserServiceImpl implements IUserService {

    @Override
    public String getUserName(String name) {
        return "BJ_"+name;
    }

    @Override
    public Integer getAge(int age ) {
        return 5+age;
    }
}
