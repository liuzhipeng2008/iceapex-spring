package bs.com.gp.iceapex.mvc.annotation;

import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GPAtuowired {
    String value() default "";
}
