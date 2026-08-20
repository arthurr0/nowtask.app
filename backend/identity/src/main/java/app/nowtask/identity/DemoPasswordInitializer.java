package app.nowtask.identity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DemoPasswordInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoPasswordInitializer.class);

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final String demoPassword;

    DemoPasswordInitializer(
            AppUserRepository users,
            PasswordEncoder encoder,
            @Value("${nowtask.demo.password:}") String demoPassword) {
        this.users = users;
        this.encoder = encoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(org.springframework.boot.ApplicationArguments args) {
        if (demoPassword.isBlank()) {
            return;
        }

        String hash = encoder.encode(demoPassword);
        int updated = 0;

        for (AppUser user : users.findAll()) {
            if (user.getPasswordHash() == null && !user.isPending()) {
                user.setPasswordHash(hash);
                updated++;
            }
        }

        if (updated > 0) {
            log.warn("A shared demo password was set for {} accounts. Do not use it outside a demo environment.", updated);
        }
    }
}
