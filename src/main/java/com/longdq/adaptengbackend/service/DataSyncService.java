package com.longdq.adaptengbackend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.dto.AIGeneratedQuestionDto;
import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import com.longdq.adaptengbackend.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;

@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final QuestionRepository questionRepository;
    private final UserLearningProgressRepository progressRepository;

    @Transactional
    public void generateAndSaveMonthlyTestQuestions(Level level) {
        String jsonResult = aiService.generateTestQuestions(level);

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new RuntimeException("Không lấy được dữ liệu từ AI");
        }

        try {
            // Sửa UNESCAPED thành UNQUOTED
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            List<AIGeneratedQuestionDto> dtos = objectMapper.readValue(
                    jsonResult,
                    new TypeReference<List<AIGeneratedQuestionDto>>() {}
            );

            for (AIGeneratedQuestionDto dto : dtos) {

                KnowledgeItem knowledgeItem = knowledgeItemRepository
                        .findByKnowledgeTypeAndLevel(dto.getKnowledgeType(), level)
                        .orElseGet(() -> {
                            KnowledgeItem newItem = new KnowledgeItem();
                            newItem.setKnowledgeName(dto.getKnowledgeName());
                            newItem.setKnowledgeType(dto.getKnowledgeType());
                            newItem.setLevel(level);
                            newItem.setPurpose(Purpose.TEST);
                            return knowledgeItemRepository.save(newItem);
                        });

                // B. Tạo Câu hỏi (Question)
                Question question = new Question();
                question.setQuestionType(dto.getQuestionType());
                question.setContent(dto.getContent());
                question.setOptions(dto.getOptions());
                question.setCorrectAnswer(dto.getCorrectAnswer());
                question.setExplanation(dto.getExplanation());
                question.setKnowledgeItem(knowledgeItem);
                question.setLevel(level);
                question.setPurpose(Purpose.TEST);
                question.setIsAnswer(false);
                question.setTargetWord(dto.getTargetWord());
                Question savedQuestion = questionRepository.save(question);
                System.out.println("✅ [NEW QUESTION] ID: " + savedQuestion.getId() +
                        " | Topic: " + (savedQuestion.getKnowledgeItem() != null ? savedQuestion.getKnowledgeItem().getKnowledgeName() : "N/A") +
                        " | Word: " + (savedQuestion.getTargetWord() != null ? savedQuestion.getTargetWord() : "None") +
                        " | Content: " + savedQuestion.getContent());
            }

            System.out.println("Đã lưu thành công 30 câu hỏi mới vào Database cho User!");

        } catch (Exception e) {
            System.err.println("Lỗi khi parse JSON hoặc lưu DB: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Transactional
    public void generateAndSaveDailyQuestions(String promptRequirement) {
        String jsonResult = aiService.generateDailyQuestionsForMissingItems(promptRequirement);
        if (jsonResult == null || jsonResult.isEmpty()) throw new RuntimeException("AI không trả về JSON");

        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            List<AIGeneratedQuestionDto> dtos = objectMapper.readValue(
                    jsonResult, new com.fasterxml.jackson.core.type.TypeReference<List<AIGeneratedQuestionDto>>() {}
            );

            for (AIGeneratedQuestionDto dto : dtos) {
                Question question = new Question();
                question.setQuestionType(dto.getQuestionType());
                question.setContent(dto.getContent());
                question.setOptions(dto.getOptions());
                question.setCorrectAnswer(dto.getCorrectAnswer());
                question.setExplanation(dto.getExplanation());

                if (dto.getKnowledgeId() != null) {
                    knowledgeItemRepository.findById(dto.getKnowledgeId()).ifPresent(question::setKnowledgeItem);
                }

                question.setLevel(Level.B1);
                question.setPurpose(Purpose.PRACTICE); // Ôn tập là PRACTICE
                question.setIsAnswer(false);
                question.setTargetWord(dto.getTargetWord());

                Question savedQuestion = questionRepository.save(question);
                System.out.println("✅ [NEW QUESTION] ID: " + savedQuestion.getId() +
                        " | Topic: " + (savedQuestion.getKnowledgeItem() != null ? savedQuestion.getKnowledgeItem().getKnowledgeName() : "N/A") +
                        " | Word: " + (savedQuestion.getTargetWord() != null ? savedQuestion.getTargetWord() : "None") +
                        " | Content: " + savedQuestion.getContent());
            }
            System.out.println("Đã nhập " + dtos.size() + " câu hỏi Ôn tập!");
        } catch (Exception e) {
            System.err.println("Lỗi parse JSON bù kho: " + e.getMessage());
        }
    }
}