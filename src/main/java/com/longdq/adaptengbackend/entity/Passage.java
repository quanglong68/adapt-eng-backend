package com.longdq.adaptengbackend.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference; // <-- Đổi Import
import com.longdq.adaptengbackend.enums.LearningTrack;
import com.longdq.adaptengbackend.enums.Skill;
import com.longdq.adaptengbackend.enums.ToeicPart;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "passages") // <-- Sửa dấu chuỗi
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Passage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PrimaryKeyJoinColumn
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_track") // <-- Sửa dấu chuỗi
    private LearningTrack learningTrack;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill") // <-- Sửa dấu chuỗi
    private Skill skill;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @OneToMany(mappedBy = "passage", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference // <-- Thay cho @JsonIgnore để cho phép kéo Questions đi kèm theo Passage
    private List<Question> questions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "toeic_part")
    private ToeicPart toeicPart;
}