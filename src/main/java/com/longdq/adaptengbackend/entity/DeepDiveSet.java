package com.longdq.adaptengbackend.entity;

import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "deep_dive_sets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeepDiveSet {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    // Bộ đề này thuộc về Knowledge Item nào (Để tiện thống kê sau này)
    @Column(name = "knowledge_item_id")
    private UUID knowledgeItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "knowledge_type", nullable = false)
    private KnowledgeType knowledgeType;

    // Từ vựng mục tiêu (Nếu là đề Ngữ pháp/Đọc hiểu thì trường này null)
    @Column(name = "target_word")
    private String targetWord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Level level;

    // Quan hệ 1-N: 1 Bộ đề có nhiều Câu hỏi (Thường là 10 câu)
    @OneToMany(mappedBy = "deepDiveSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeepDiveQuestion> questions = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}