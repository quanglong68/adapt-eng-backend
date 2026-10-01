package com.longdq.adaptengbackend.modules.toeic.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longdq.adaptengbackend.modules.toeic.dto.AIGeneratedQuestionDto;
import com.longdq.adaptengbackend.modules.toeic.dto.ToeicAIGeneratedDto;
import com.longdq.adaptengbackend.modules.spacedrepetition.entity.KnowledgeItem;
import com.longdq.adaptengbackend.modules.toeic.entity.Passage;
import com.longdq.adaptengbackend.modules.toeic.entity.Question;
import com.longdq.adaptengbackend.common.exception.AIProcessingException;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.KnowledgeItemRepository;
import com.longdq.adaptengbackend.modules.toeic.repository.PassageRepository;
import com.longdq.adaptengbackend.modules.toeic.repository.QuestionRepository;
import com.longdq.adaptengbackend.modules.spacedrepetition.repository.UserLearningProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.longdq.adaptengbackend.common.ai.AIService;
import com.longdq.adaptengbackend.common.enums.KnowledgeType;
import com.longdq.adaptengbackend.common.enums.LearningTrack;
import com.longdq.adaptengbackend.common.enums.Level;
import com.longdq.adaptengbackend.common.enums.Purpose;
import com.longdq.adaptengbackend.common.enums.QuestionType;
import com.longdq.adaptengbackend.common.enums.Skill;
import com.longdq.adaptengbackend.common.enums.ToeicPart;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final AIService aiService;
    private final ObjectMapper objectMapper;
    private final KnowledgeItemRepository knowledgeItemRepository;
    private final QuestionRepository questionRepository;
    private final UserLearningProgressRepository progressRepository;
    private final PassageRepository passageRepository;

    private boolean isVocabularyType(KnowledgeType type) {
        if (type == null) {
            return false;
        }
        return type == KnowledgeType.VOCABULARY
                || type == KnowledgeType.COLLOCATIONS
                || type == KnowledgeType.PHRASAL_VERBS
                || type == KnowledgeType.WORD_FORMATION
                || type == KnowledgeType.SYNONYM;
    }

    @Transactional
    public void generateAndSaveMonthlyTestQuestions(Level level) {
        String jsonResult = aiService.generateTestQuestions(level);

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new AIProcessingException("Không lấy được dữ liệu từ AI");
        }

        try {
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

                Question question = buildGeneralQuestion(dto, knowledgeItem, level, Purpose.TEST);
                Question savedQuestion = questionRepository.save(question);
                logSavedQuestion(savedQuestion);
            }

            log.info("Successfully saved 30 new test questions for level {}", level);

        } catch (AIProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse JSON or save monthly test questions: {}", e.getMessage(), e);
            throw new AIProcessingException(e.getMessage(), e);
        }
    }

    @Transactional
    public void generateAndSaveDailyQuestions(String promptRequirement) {
        String jsonResult = aiService.generateDailyQuestionsForMissingItems(promptRequirement);
        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new AIProcessingException("AI không trả về JSON");
        }

        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            List<AIGeneratedQuestionDto> dtos = objectMapper.readValue(
                    jsonResult, new TypeReference<List<AIGeneratedQuestionDto>>() {}
            );

            for (AIGeneratedQuestionDto dto : dtos) {
                Question question = new Question();
                question.setQuestionType(dto.getQuestionType());
                question.setContent(dto.getContent());
                question.setOptions(dto.getOptions());
                question.setCorrectAnswer(dto.getCorrectAnswer());
                question.setExplanation(dto.getExplanation());
                question.setLevel(Level.B1);
                question.setPurpose(Purpose.PRACTICE);
                question.setIsAnswer(false);

                if (dto.getKnowledgeId() != null) {
                    knowledgeItemRepository.findById(dto.getKnowledgeId()).ifPresent(ki -> {
                        question.setKnowledgeItem(ki);
                        if (isVocabularyType(ki.getKnowledgeType())) {
                            question.setTargetWord(dto.getTargetWord());
                        } else {
                            question.setTargetWord(null);
                        }
                    });
                } else {
                    question.setTargetWord(null);
                }

                Question savedQuestion = questionRepository.save(question);
                logSavedQuestion(savedQuestion);
            }
            log.info("Imported {} daily practice questions", dtos.size());
        } catch (Exception e) {
            log.error("Failed to parse daily refill JSON: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public void generateAndSaveToeicPassage(
            Level level, ToeicPart toeicPart, KnowledgeItem knowledgeItem, String targetWord, Purpose purpose) {
        String jsonResult = switch (toeicPart) {
            case PART_6 -> aiService.generateToeicPart6Single(
                    level, knowledgeItem != null ? knowledgeItem.getKnowledgeType() : null, targetWord);
            case PART_7_SINGLE -> aiService.generateToeicPart7Single(level, targetWord);
            case PART_7_MULTIPLE -> aiService.generateToeicPart7Multiple(level, targetWord);
            default -> null;
        };

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new AIProcessingException("AI không trả về JSON cho phần thi TOEIC: " + toeicPart.name());
        }

        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            ToeicAIGeneratedDto dto = objectMapper.readValue(jsonResult, ToeicAIGeneratedDto.class);

            Passage passage = new Passage();
            passage.setLearningTrack(LearningTrack.TOEIC);
            passage.setSkill(Skill.READING);
            passage.setContent(dto.getPassageContent());
            passage.setToeicPart(toeicPart);

            Passage savedPassage = passageRepository.save(passage);

            for (ToeicAIGeneratedDto.ToeicQuestionDto qDto : dto.getQuestions()) {
                saveToeicQuestion(qDto, level, toeicPart, purpose, savedPassage);
            }
        } catch (AIProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new AIProcessingException("Lỗi parse JSON Bài đọc TOEIC: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void generateAndSaveToeicPart5(Level level, KnowledgeItem knowledgeItem, String targetWord, Purpose purpose) {
        KnowledgeType specificType = knowledgeItem != null ? knowledgeItem.getKnowledgeType() : null;

        // Thêm số 15 vào đây
        String jsonResult = aiService.generateToeicPart5(level, specificType, targetWord, 15);

        if (jsonResult == null || jsonResult.isEmpty()) {
            throw new AIProcessingException("AI không trả về JSON cho TOEIC PART 5");
        }

        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            List<ToeicAIGeneratedDto.ToeicQuestionDto> dtos = objectMapper.readValue(
                    jsonResult, new TypeReference<List<ToeicAIGeneratedDto.ToeicQuestionDto>>() {}
            );

            for (ToeicAIGeneratedDto.ToeicQuestionDto qDto : dtos) {
                saveToeicQuestion(qDto, level, ToeicPart.PART_5, purpose, null);
            }
        } catch (AIProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new AIProcessingException("Lỗi parse JSON TOEIC PART 5: " + e.getMessage(), e);
        }
    }

    private Question buildGeneralQuestion(
            AIGeneratedQuestionDto dto, KnowledgeItem knowledgeItem, Level level, Purpose purpose) {
        Question question = new Question();
        question.setQuestionType(dto.getQuestionType());
        question.setContent(dto.getContent());
        question.setOptions(dto.getOptions());
        question.setCorrectAnswer(dto.getCorrectAnswer());
        question.setExplanation(dto.getExplanation());
        question.setKnowledgeItem(knowledgeItem);
        question.setLevel(level);
        question.setPurpose(purpose);
        question.setIsAnswer(false);
        if (isVocabularyType(dto.getKnowledgeType())) {
            question.setTargetWord(dto.getTargetWord());
        } else {
            question.setTargetWord(null);
        }
        return question;
    }

    // ĐÃ SỬA: Đổi kiểu trả về từ 'void' thành 'Question'
    private Question saveToeicQuestion(
            ToeicAIGeneratedDto.ToeicQuestionDto qDto, Level level, ToeicPart toeicPart, Purpose purpose, Passage passage) {
        Question question = new Question();
        question.setLearningTrack(LearningTrack.TOEIC);
        question.setSkill(Skill.READING);
        question.setToeicPart(toeicPart);
        question.setQuestionType(QuestionType.MULTIPLE_CHOICE);
        question.setContent(qDto.getContent());
        question.setOptions(qDto.getOptions());
        question.setCorrectAnswer(qDto.getCorrectAnswer());
        question.setExplanation(qDto.getExplanation());
        question.setLevel(level);
        question.setPurpose(purpose);
        question.setIsAnswer(false);
        if (isVocabularyType(qDto.getKnowledgeType())) {
            question.setTargetWord(qDto.getTargetWord());
        } else {
            question.setTargetWord(null);
        }
        question.setPassage(passage);

        if (qDto.getKnowledgeType() != null) {
            KnowledgeItem ki = knowledgeItemRepository
                    .findByKnowledgeTypeAndLevel(qDto.getKnowledgeType(), level)
                    .orElseGet(() -> {
                        KnowledgeItem newItem = new KnowledgeItem();
                        newItem.setKnowledgeType(qDto.getKnowledgeType());
                        newItem.setLevel(level);
                        newItem.setKnowledgeName(
                                qDto.getKnowledgeName() != null ? qDto.getKnowledgeName() : qDto.getKnowledgeType().name());
                        newItem.setPurpose(purpose);
                        return knowledgeItemRepository.save(newItem);
                    });
            question.setKnowledgeItem(ki);
        }
        // ĐÃ SỬA: return lại Question sau khi save
        return questionRepository.save(question);
    }

    // =========================================================================
    // 🚀 HÀM MỚI CHO DEEP DIVE: TÁI SỬ DỤNG HOÀN TOÀN LOGIC CŨ
    // =========================================================================
    @Transactional
    public java.util.List<Long> generateDeepDiveQuestions(Level level, KnowledgeItem knowledgeItem, String targetWord) {
        KnowledgeType specificType = knowledgeItem != null ? knowledgeItem.getKnowledgeType() : null;
        boolean isVocabulary = (targetWord != null && !targetWord.trim().isEmpty());

        java.util.List<Long> finalQuestionIds = new java.util.ArrayList<>();

        try {
            objectMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);

            // --- BƯỚC 1: GEN PART 5 (10 CÂU HOẶC 6 CÂU TÙY LOẠI) ---
            int p5Quantity = isVocabulary ? 6 : 10;
            String p5Json = aiService.generateToeicPart5(level, specificType, targetWord, p5Quantity);

            if (p5Json != null && !p5Json.isEmpty()) {
                java.util.List<ToeicAIGeneratedDto.ToeicQuestionDto> p5Dtos = objectMapper.readValue(
                        p5Json, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<ToeicAIGeneratedDto.ToeicQuestionDto>>() {}
                );

                for (ToeicAIGeneratedDto.ToeicQuestionDto qDto : p5Dtos) {
                    // TÁI SỬ DỤNG HÀM CŨ Ở ĐÂY, VỪA LƯU VỪA LẤY ĐƯỢC ID
                    Question savedQ = saveToeicQuestion(qDto, level, ToeicPart.PART_5, Purpose.PRACTICE, null);
                    finalQuestionIds.add(savedQ.getId());
                }
            }

            // --- BƯỚC 2: GEN THÊM PART 6 (CHỈ CHO TỪ VỰNG) ---
            if (isVocabulary) {
                String p6Json = aiService.generateToeicPart6Single(level, specificType, targetWord);
                if (p6Json != null && !p6Json.isEmpty()) {
                    ToeicAIGeneratedDto p6Dto = objectMapper.readValue(p6Json, ToeicAIGeneratedDto.class);

                    Passage passage = new Passage();
                    passage.setLearningTrack(LearningTrack.TOEIC);
                    passage.setSkill(Skill.READING);
                    passage.setContent(p6Dto.getPassageContent());
                    passage.setToeicPart(ToeicPart.PART_6);
                    Passage savedPassage = passageRepository.save(passage);

                    for (ToeicAIGeneratedDto.ToeicQuestionDto qDto : p6Dto.getQuestions()) {
                        // TÁI SỬ DỤNG HÀM CŨ, TRUYỀN THÊM PASSAGE VÀO
                        Question savedQ = saveToeicQuestion(qDto, level, ToeicPart.PART_6, Purpose.PRACTICE, savedPassage);
                        finalQuestionIds.add(savedQ.getId());
                    }
                }
            }

            return finalQuestionIds;

        } catch (Exception e) {
            throw new AIProcessingException("Lỗi sinh đề Deep Dive: " + e.getMessage(), e);
        }
    }

    private void logSavedQuestion(Question savedQuestion) {
        log.info("[NEW QUESTION] ID: {} | Topic: {} | Word: {} | Content: {}",
                savedQuestion.getId(),
                savedQuestion.getKnowledgeItem() != null ? savedQuestion.getKnowledgeItem().getKnowledgeName() : "N/A",
                savedQuestion.getTargetWord() != null ? savedQuestion.getTargetWord() : "None",
                savedQuestion.getContent());
    }
}