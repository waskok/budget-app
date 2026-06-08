// pasir-l01-waskok-main/src/main/java/pk/ni/pasir_wasko_klaudiusz/service/GroupTransactionService.java
package pk.ni.pasir_wasko_klaudiusz.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pk.ni.pasir_wasko_klaudiusz.dto.GroupTransactionDTO;
import pk.ni.pasir_wasko_klaudiusz.model.Debt;
import pk.ni.pasir_wasko_klaudiusz.model.Group;
import pk.ni.pasir_wasko_klaudiusz.model.Membership;
import pk.ni.pasir_wasko_klaudiusz.model.Transaction;
import pk.ni.pasir_wasko_klaudiusz.model.TransactionType;
import pk.ni.pasir_wasko_klaudiusz.model.User;
import pk.ni.pasir_wasko_klaudiusz.repository.DebtRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.GroupRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.MembershipRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.TransactionRepository;
import pk.ni.pasir_wasko_klaudiusz.security.JwtUtil;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GroupTransactionService extends TextWebSocketHandler {
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;
    private final DebtRepository debtRepository;
    private final MembershipService membershipService;
    private final TransactionRepository transactionRepository;
    private final JwtUtil jwtUtil;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public GroupTransactionService(GroupRepository groupRepository, MembershipRepository membershipRepository, DebtRepository debtRepository, MembershipService membershipService, TransactionRepository transactionRepository, JwtUtil jwtUtil) {
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.debtRepository = debtRepository;
        this.membershipService = membershipService;
        this.transactionRepository = transactionRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri != null && uri.getQuery() != null) {
            String query = uri.getQuery();
            String token = null;
            for (String param : query.split("&")) {
                String[] pair = param.split("=");
                if (pair.length > 1 && "token".equals(pair[0])) {
                    token = pair[1];
                    break;
                }
            }
            if (token != null && jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractUsername(token);
                session.getAttributes().put("email", email);
                sessions.put(email, session);
                System.out.println("Połączono pomyślnie z WebSocket dla: " + email);
                return;
            }
        }
        session.close(CloseStatus.NOT_ACCEPTABLE);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String email = (String) session.getAttributes().get("email");
        if (email != null) {
            sessions.remove(email);
            System.out.println("Rozłączono sesję WebSocket dla: " + email);
        }
    }

    public void addGroupTransaction(GroupTransactionDTO transactionDTO, User currentUser) {
        Group group = groupRepository.findById(transactionDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono Grupy"));
        membershipService.assertCurrentUserIsGroupMember(group.getId());
        List<Membership> members = membershipRepository.findByGroupId(group.getId());
        List<Membership> selectedMembers = selectParticipants(transactionDTO, members, currentUser);

        if (selectedMembers.isEmpty()) {
            throw new IllegalStateException("Grupa nie ma członków, nie można dodać transakcji.");
        }

        double amountPerUser = transactionDTO.getAmount() / selectedMembers.size();

        TransactionType tType = TransactionType.EXPENSE;
        if ("INCOME".equals(transactionDTO.getType())) {
            tType = TransactionType.INCOME;
        }

        Transaction personalTransaction = new Transaction();
        personalTransaction.setAmount(transactionDTO.getAmount());
        personalTransaction.setType(tType);
        personalTransaction.setNotes("Wydatek grupowy: " + transactionDTO.getTitle());
        personalTransaction.setTags("Grupa: " + group.getName());
        personalTransaction.setUser(currentUser);
        personalTransaction.setTimestamp(LocalDateTime.now());
        transactionRepository.save(personalTransaction);

        boolean expense = (tType == TransactionType.EXPENSE);

        for (Membership member : selectedMembers) {
            User otherUser = member.getUser();
            if (!otherUser.getId().equals(currentUser.getId())) {
                Debt debt = new Debt();
                debt.setDebtor(expense ? otherUser : currentUser);
                debt.setCreditor(expense ? currentUser : otherUser);
                debt.setGroup(group);
                debt.setAmount(amountPerUser);
                debt.setTitle(transactionDTO.getTitle());
                debtRepository.save(debt);

                String customMessage = String.format(Locale.US, "%s dodał wydatek \"%s\" w grupie %s. Twoja część: %.2f zł.",
                        currentUser.getEmail(), transactionDTO.getTitle(), group.getName(), amountPerUser);

                String jsonMessage = String.format(Locale.US,
                        "{\"type\":\"GROUP_EXPENSE_ADDED\"," +
                                "\"groupId\":%d," +
                                "\"groupName\":\"%s\"," +
                                "\"title\":\"%s\"," +
                                "\"amount\":%.2f," +
                                "\"userShare\":%.2f," +
                                "\"createdByEmail\":\"%s\"," +
                                "\"message\":\"%s\"}",
                        group.getId(),
                        group.getName().replace("\"", "\\\""),
                        transactionDTO.getTitle().replace("\"", "\\\""),
                        transactionDTO.getAmount(),
                        amountPerUser,
                        currentUser.getEmail(),
                        customMessage.replace("\"", "\\\"")
                );

                WebSocketSession otherUserSession = sessions.get(otherUser.getEmail());
                if (otherUserSession != null && otherUserSession.isOpen()) {
                    try {
                        otherUserSession.sendMessage(new TextMessage(jsonMessage));
                        System.out.println("Wysłano powiadomienie WebSocket do: " + otherUser.getEmail());
                    } catch (Exception e) {
                        System.err.println("Błąd wysyłania przez WebSocket: " + e.getMessage());
                    }
                }
            }
        }
    }

    private List<Membership> selectParticipants(GroupTransactionDTO transactionDTO, List<Membership> members, User currentUser) {
        List<Long> selectedUserIds = transactionDTO.getSelectedUserIds();
        if (selectedUserIds == null || selectedUserIds.isEmpty()) {
            return members;
        }

        Set<Long> uniqueSelectedUserIds = new HashSet<>(selectedUserIds);
        List<Membership> selectedMembers = members.stream()
                .filter(membership -> uniqueSelectedUserIds.contains(membership.getUser().getId()))
                .toList();

        if (selectedMembers.size() != uniqueSelectedUserIds.size()) {
            throw new IllegalStateException("Wszyscy wybrani uzytkownicy musza byc członkami grupy.");
        }

        boolean currentUserSelected = selectedMembers.stream()
                .anyMatch(membership -> membership.getUser().getId().equals(currentUser.getId()));
        if (!currentUserSelected) {
            throw new IllegalStateException("Aktualny uzytkownik musi byc uczestnikiem transakcji grupowej.");
        }

        if (selectedMembers.size() < 2) {
            throw new IllegalStateException("Transakcja grupowa wymaga co najmniej dwoch uczestnikow.");
        }

        return selectedMembers;
    }
}