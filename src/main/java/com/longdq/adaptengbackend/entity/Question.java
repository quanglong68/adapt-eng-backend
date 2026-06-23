package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.*;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_knowledge_item", columnList = "knowledge_item_id"),
        @Index(name = "idx_target_word", columnList = "target_word")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "knowledge_item_id")
    private KnowledgeItem knowledgeItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type")
    private QuestionType questionType;

    @Column(columnDefinition = "TEXT")
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> options;

    @Column(name = "correct_answer")
    private String correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    private Boolean isAnswer;

    @Enumerated(EnumType.STRING)
    private Level level;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    @Column(name = "target_word")
    private String targetWord;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_track")
    private LearningTrack learningTrack;

    @Enumerated(EnumType.STRING)
    @Column(name = "skill")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "toeic_part")
    private ToeicPart toeicPart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passage_id")
    @com.fasterxml.jackson.annotation.JsonBackReference
    private Passage passage;
}