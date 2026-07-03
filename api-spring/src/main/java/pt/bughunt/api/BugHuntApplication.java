package pt.bughunt.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import pt.bughunt.mentor.ClaudeLlmClient;
import pt.bughunt.mentor.SocraticMentor;
import pt.bughunt.sandbox.CommandRunner;
import pt.bughunt.sandbox.SandboxExecutor;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Arranque da aplicação + wiring das dependências.
 *
 * Correr com:
 *   export ANTHROPIC_API_KEY=...
 *   mvn spring-boot:run
 */
@SpringBootApplication
public class BugHuntApplication {

    public static void main(String[] args) {
        SpringApplication.run(BugHuntApplication.class, args);
    }

    @Bean
    public SandboxExecutor sandboxExecutor() throws Exception {
        Path workspace = Files.createDirectories(Path.of("/var/bughunt/workspaces"));
        return new SandboxExecutor(new CommandRunner.SystemCommandRunner(), workspace);
    }

    @Bean
    public SocraticMentor socraticMentor() {
        return new SocraticMentor(new ClaudeLlmClient());
    }
}
