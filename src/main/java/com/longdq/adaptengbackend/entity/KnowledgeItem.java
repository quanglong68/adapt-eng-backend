package com.longdq.adaptengbackend.entity;


import com.longdq.adaptengbackend.enums.KnowledgeType;
import com.longdq.adaptengbackend.enums.Level;
import com.longdq.adaptengbackend.enums.Purpose;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name ="knowledge_items")
@AllArgsConstructor
@NoArgsConstructor
@Data

//khoan hẵng code, quên cái bạn vừa hướng dẫn tôi đi, ta phải xác định được rõ ràng thời
// điểm để mà gọi câu lệnh này, đầu tiên là lúc user mới bắt đầu học app/web lần đầu, họ
// chọn trình độ, và mình sẽ gen câu hỏi dựa trên trình độ này, cái này thì tôi nghĩ sẽ chia ra
// nhiều trinh độ và mỗi tháng sẽ gen ra một bộ câu hỏi mới một lần, tránh bắt user phải đợi gen câu hỏi sau
// khi chọn trình độ, như vậy lúc này AI sẽ được gọi 1 lần, lúc này tôi nghĩ sẽ lưu biến câu hỏi vào nhưng
// là câu hỏi để test chưa được gắn bất cứ knowledge_item_id nào, câu promt lúc này sẽ phai lưu ý cho việc questiontype
// phải là 1 trong các MULTIPLE_CHOICE, isAnswer mặc định là false trong entity Question
//
//,  tiếp theo, sau khi người dùng trả lời xong các câu hỏi test trình độ đó, lúc này, sẽ có một câu prompt
// tiếp theo gửi lại các câu hỏi người dùng sai, để xin lấy các thuộc tính của KnowledgeItem theo 1 khuôn
// khổ nhất định kiểu, type chỉ được thuộc các kiểu TỪ_LOẠI, CÂU_ĐIỀU_KIỆN_LOẠI_1, hay type ngữ pháp tiếng anh nào
// đó, content thì là gì gì đó, des, là gì gì đó, cà cate là gì gì đó, lúc này AI sẽ gen ra các enitty để lưu vào
// rồi lấy question sai đó gắn knowledge item mà ai vừa trả về, rồi lưu tiến trình các thứ, ai sử dụng thứ 3 chính là
// AI được gọi để gen câu hỏi khi đến ngay ôn tập, con AI lúc này sẽ gen câu hỏi, khi nhận được các data từ KnowledgeItem
// từ itemType, content, description, category sao cho cá nhân hóa cho từng người dùng nhất có thể,
public class KnowledgeItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "knowledge_type")
    @Enumerated(EnumType.STRING)
    private KnowledgeType knowledgeType;

    private String knowledgeName;


    @Enumerated(EnumType.STRING)
    private Level level;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;
}
