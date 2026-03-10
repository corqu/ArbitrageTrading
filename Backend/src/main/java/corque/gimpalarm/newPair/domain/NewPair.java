package corque.gimpalarm.newPair.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
public class NewPair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Column(columnDefinition = "TEXT")
    private String address;
    private LocalDate listedAt;
    private String dexName;
}
