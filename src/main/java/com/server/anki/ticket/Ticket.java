package com.server.anki.ticket;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.server.anki.chat.entity.Chat;
import com.server.anki.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
@Getter
@Setter
@Table(name = "ticket")
@Entity
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue")
    private String issue;
    @Column(name = "type")
    private int type;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Column(name = "closed_date")
    private LocalDateTime closedDate;

    @Column(name = "open", nullable = false)
    private boolean open = true;

    @Column(name = "closed_by_admin", nullable = false)
    private boolean closedByAdmin;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonBackReference
    private User user;

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<Chat> chats = new HashSet<>();  // 初始化集合并添加级联操作

    @ManyToOne
    @JoinColumn(name = "closed_by_user_id")
    @JsonBackReference
    private User closedBy;


    @ManyToOne
    @JoinColumn(name = "assigned_admin_id")
    private User assignedAdmin;

    // 添加方便的方法来管理关联关系
    public void addChat(Chat chat) {
        if (chats == null) {
            chats = new HashSet<>();
        }
        chats.add(chat);
        chat.setTicket(this);
    }

    public void removeChat(Chat chat) {
        if (chats != null) {
            chats.remove(chat);
            chat.setTicket(null);
        }
    }
}
