// pasir-l01-waskok-main/src/main/java/pk/ni/pasir_wasko_klaudiusz/service/DebtService.java
package pk.ni.pasir_wasko_klaudiusz.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import pk.ni.pasir_wasko_klaudiusz.dto.DebtDTO;
import pk.ni.pasir_wasko_klaudiusz.model.Debt;
import pk.ni.pasir_wasko_klaudiusz.model.Group;
import pk.ni.pasir_wasko_klaudiusz.model.Transaction;
import pk.ni.pasir_wasko_klaudiusz.model.TransactionType;
import pk.ni.pasir_wasko_klaudiusz.model.User;
import pk.ni.pasir_wasko_klaudiusz.repository.DebtRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.GroupRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.UserRepository;
import pk.ni.pasir_wasko_klaudiusz.repository.TransactionRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DebtService {
    private final DebtRepository debtRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final CurrentUserService currentUserService;
    private final TransactionRepository transactionRepository;

    public DebtService(DebtRepository debtRepository, GroupRepository groupRepository, UserRepository userRepository, MembershipService membershipService, CurrentUserService currentUserService, TransactionRepository transactionRepository) {
        this.debtRepository = debtRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.membershipService = membershipService;
        this.currentUserService = currentUserService;
        this.transactionRepository = transactionRepository;
    }

    public List<Debt> getGroupDebts(Long groupId) {
        membershipService.assertCurrentUserIsGroupMember(groupId);
        return debtRepository.findByGroupId(groupId);
    }

    public Debt createDebt(DebtDTO debtDTO) {
        Group group = groupRepository.findById(debtDTO.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Nie można utworzyć długu. Grupa o ID " + debtDTO.getGroupId() + " nie istnieje."));

        User debtor = userRepository.findById(debtDTO.getDebtorId())
                .orElseThrow(() -> new EntityNotFoundException("Nie można utworzyć długu. Dłużnik o ID " + debtDTO.getDebtorId() + " nie istnieje."));

        User creditor = userRepository.findById(debtDTO.getCreditorId())
                .orElseThrow(() -> new EntityNotFoundException("Nie można utworzyć długu. Wierzyciel o ID " + debtDTO.getCreditorId() + " nie istnieje."));

        membershipService.assertCurrentUserIsGroupMember(group.getId());
        membershipService.assertUserIsGroupMember(group.getId(), debtor.getId());
        membershipService.assertUserIsGroupMember(group.getId(), creditor.getId());

        if (debtor.getId().equals(creditor.getId())) {
            throw new IllegalStateException("Dłużnik i wierzyciel muszą być różnymi użytkownikami.");
        }

        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(group, debtor, creditor, currentUser);

        Debt debt = new Debt();
        debt.setGroup(group);
        debt.setDebtor(debtor);
        debt.setCreditor(creditor);
        debt.setAmount(debtDTO.getAmount());
        debt.setTitle(debtDTO.getTitle());

        return debtRepository.save(debt);
    }

    public void deleteDebt(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException("Nie można usunąć długu. Dług o ID " + debtId + " nie istnieje."));

        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        User currentUser = currentUserService.getCurrentUser();
        assertCurrentUserCanManageDebt(debt.getGroup(), debt.getDebtor(), debt.getCreditor(), currentUser);

        debtRepository.delete(debt);
    }

    public Debt markDebtAsPaid(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();
        if (!debt.getDebtor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko dłużnik może oznaczyć dług jako opłacony.");
        }
        debt.setPaidByDebtor(true);
        debt.setConfirmedByCreditor(false);
        return debtRepository.save(debt);
    }

    public Debt confirmDebtPayment(Long debtId) {
        Debt debt = getDebtForCurrentGroupMember(debtId);
        User currentUser = currentUserService.getCurrentUser();
        if (!debt.getCreditor().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("Tylko wierzyciel może potwierdzić spłatę długu.");
        }
        if (!debt.isPaidByDebtor()) {
            throw new IllegalStateException("Dług musi zostać najpierw oznaczony jako opłacony przez dłużnika.");
        }
        debt.setConfirmedByCreditor(true);

        Transaction debtorTx = new Transaction();
        debtorTx.setAmount(debt.getAmount());
        debtorTx.setType(TransactionType.EXPENSE);
        debtorTx.setNotes("Spłata długu: " + debt.getTitle());
        debtorTx.setTags("Grupa: " + debt.getGroup().getName());
        debtorTx.setUser(debt.getDebtor());
        debtorTx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(debtorTx);

        Transaction creditorTx = new Transaction();
        creditorTx.setAmount(debt.getAmount());
        creditorTx.setType(TransactionType.INCOME);
        creditorTx.setNotes("Otrzymano spłatę: " + debt.getTitle());
        creditorTx.setTags("Grupa: " + debt.getGroup().getName());
        creditorTx.setUser(debt.getCreditor());
        creditorTx.setTimestamp(LocalDateTime.now());
        transactionRepository.save(creditorTx);

        return debtRepository.save(debt);
    }

    private Debt getDebtForCurrentGroupMember(Long debtId) {
        Debt debt = debtRepository.findById(debtId)
                .orElseThrow(() -> new EntityNotFoundException("Nie znaleziono długu o ID " + debtId + "."));
        membershipService.assertCurrentUserIsGroupMember(debt.getGroup().getId());
        return debt;
    }

    private void assertCurrentUserCanManageDebt(Group group, User debtor, User creditor, User currentUser) {
        boolean isGroupOwner = group.getOwner().getId().equals(currentUser.getId());
        boolean isDebtParticipant = debtor.getId().equals(currentUser.getId()) || creditor.getId().equals(currentUser.getId());

        if (!isGroupOwner && !isDebtParticipant) {
            throw new AccessDeniedException("Tylko właściciel grupy albo uczestnik długu może wykonać te operacje.");
        }
    }
}