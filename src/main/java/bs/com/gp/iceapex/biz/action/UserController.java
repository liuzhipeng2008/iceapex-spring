package bs.com.gp.iceapex.biz.action;

import bs.com.gp.iceapex.biz.service.IUserService;
import bs.com.gp.iceapex.mvc.annotation.GPAtuowired;
import bs.com.gp.iceapex.mvc.annotation.GPController;
import bs.com.gp.iceapex.mvc.annotation.GPRequestMapping;
import bs.com.gp.iceapex.mvc.annotation.GPRequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@GPController
@GPRequestMapping("/user")
public class UserController {

    @GPAtuowired
    private IUserService userService;

    @GPRequestMapping("/getUserName")
    public void getUserName(HttpServletRequest request, HttpServletResponse response, @GPRequestParam("name") String name){
        String userName = this.userService.getUserName(name);
        try {
            response.getWriter().write("My name is " + userName);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @GPRequestMapping("/getUserAge")
    public void getUserAge(HttpServletRequest request, HttpServletResponse response, @GPRequestParam("age") Integer age){
        int userAge = this.userService.getAge(age);
        try {
            response.getWriter().write("I am  " + userAge+" years old !");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
