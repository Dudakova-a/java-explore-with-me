package ru.practicum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Autowired;
import ru.practicum.repository.StatsRepository;

@SpringBootApplication
public class StatsServerApplication implements CommandLineRunner {

    @Autowired
    private StatsRepository statsRepository;

    public static void main(String[] args) {
        SpringApplication.run(StatsServerApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        statsRepository.deleteAll();
        System.out.println("✅ База данных очищена при запуске приложения");
    }
}