package com.longdq.adaptengbackend.service;

import com.longdq.adaptengbackend.dto.DailyQuestionResponse;
import com.longdq.adaptengbackend.entity.KnowledgeItem;
import com.longdq.adaptengbackend.entity.Question;
import com.longdq.adaptengbackend.entity.UserLearningProgress;
import com.longdq.adaptengbackend.repository.QuestionRepository;
import com.longdq.adaptengbackend.repository.UserLearningProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// ở đây khi mà trước ngày ôn tập, hệ thống sẽ tự call để lấy cái UserLearingProgress đã đến ngày phải ôn tập lại, mặc định mỗi ngày
//sẽ ôn tập 30 câu là hợp lí vừa không quá nhiều để người dùng có thể ôn, không quá ít số lượng câu hỏi
//nếu 1 ngày có lớn hơn 30 chủ điểm ngữ pháp để ôn, hoặc hết ngày mà câu hỏi chưa được trả lời
// -> ưu tiên sang ngày hôm sau phải ôn tập chủ điểm ngữ pháp mà ngày hôm qua vừa sai hoặc mới đúng 1 lần trước
// -> ưu tiên sau đó là các chủ điểm ngữ pháp mà hôm qua phải ôn nhưng vì giới hạn 30 câu hỏi nên chưa được ôn
// -> cuối cùng mới là các câu hỏi đến hạn phải ôn
// để tránh việc ôn lại các câu hỏi đã quá lâu không được ôn do việc lười học dẫn đến tông đọng câu hỏi
//hệ thống sẽ tự xóa những chủ điểm ngữ pháp có ngày hiện tại đã vượt qua nextReviewDate 3 tháng
//khi mà trước ngày AI gen ra bộ câu hỏi mới, nó sẽ gen ra  chủ điểm ngữ pháp mà ngày hôm qua vừa sai hoặc mới đúng 1 lần trước
//nếu mà đủ 30 câu thì thôi còn nếu chưa đủ thì sẽ lấy các câu hỏi cũ chưa được trả lời ra ôn, nhờ vậy đảm bảo trong khi lúc nào
//cũng sẽ có bé hơn 30 câu hỏi chưa được trả lời
//câu hỏi sẽ được hiển thị lên fe sẽ có dạng: đây có phải dạng câu hỏi bạn đã từng sai hay không ?
//đề bài và đáp án cùng với lới giải thích cho đáp án
//mặc định một chủ điểm ngữ pháp sẽ gen ra một câu hỏi
@Service
@RequiredArgsConstructor
public class LearningService {
    private final UserLearningProgressRepository userLearningProgressRepository;
    private final QuestionRepository questionRepository;
    private static final int DAILY_REVIEW_QUESTION_COUNT = 30;
    public List<DailyQuestionResponse> getDailyQuestions(UUID userId){
        Pageable pageable = PageRequest.of(0, DAILY_REVIEW_QUESTION_COUNT, Sort.by(Sort.Direction.ASC, "repetitionCount")
                .and(Sort.by(Sort.Direction.DESC, "nextReviewDate")));
        List<UserLearningProgress> dueProgresses = userLearningProgressRepository.findByUserIdAndNextReviewDateLessThanEqual(userId,LocalDateTime.now(),pageable);
        if(dueProgresses.isEmpty()){
            return Collections.emptyList();
        }
        List<UUID> knowledgeItemIds =  dueProgresses.stream().map(progress -> progress.getKnowledgeItem().getId()).toList();

        List<Question> questions = questionRepository.findByKnowledgeItemIdIn(knowledgeItemIds);

        Map<UUID, Question> questionMap = questions.stream().collect(Collectors.toMap(q -> q.getKnowledgeItem().getId(), q -> q,
                (q1,q2) -> q1));

        return dueProgresses.stream().filter( p -> questionMap.containsKey(p.getKnowledgeItem().getId()))
                .map(p ->{
                    Question matchedQuestion = questionMap.get(p.getKnowledgeItem().getId());
                    return new DailyQuestionResponse(matchedQuestion.getId(),
                            matchedQuestion.getQuestionType(),
                            matchedQuestion.getContent(),
                            matchedQuestion.getOptions(),
                            p.getId());
                }).toList();

    }
}
