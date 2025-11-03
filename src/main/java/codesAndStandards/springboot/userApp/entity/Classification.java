package codesAndStandards.springboot.userApp.entity;

import codesAndStandards.springboot.userApp.entity.Document;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Classification")
public class Classification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "classification_id")
    private Long id;

    @Column(name = "classification_name", nullable = false, unique = true)
    private String classificationName;

    @ManyToMany(mappedBy = "classifications")
    private Set<Document> documents = new HashSet<>();
}