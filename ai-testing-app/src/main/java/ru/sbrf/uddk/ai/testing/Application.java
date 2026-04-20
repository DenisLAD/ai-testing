package ru.sbrf.uddk.ai.testing;

import ru.sbrf.uddk.ai.testing.entity.TestSession;
import ru.sbrf.uddk.ai.testing.entity.consts.TestGoal;
import ru.sbrf.uddk.ai.testing.service.TestAgentService;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

import java.util.UUID;

@SpringBootApplication
@ComponentScan(basePackages = "ru.sbrf.uddk.ai.testing")
public class Application {
    public static void main(String[] args) {
        SpringApplication sa = new SpringApplication(Application.class);
        sa.setBannerMode(Banner.Mode.OFF);
        sa.setWebApplicationType(WebApplicationType.SERVLET);
        ConfigurableApplicationContext applicationContext = sa.run(args);

//
//        TestSession testSession = new TestSession();
//        testSession.setGoal(TestGoal.EXPLORATORY);
////        testSession.setTargetUrl("https://rzd.ru");
//        testSession.setTargetUrl("https://the-internet.herokuapp.com/login");
//        testSession.setId(UUID.randomUUID());
//        applicationContext.getBean(TestAgentService.class).run(testSession);
    }
}
