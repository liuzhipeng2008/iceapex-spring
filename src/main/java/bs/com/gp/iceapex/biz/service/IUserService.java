package bs.com.gp.iceapex.biz.service;

public interface IUserService {

    /**
     * 获取用户名
     * @return
     */
    String getUserName(String name);

    /**
     * 获取年龄
     * @return
     */
    Integer getAge(int age);

}
