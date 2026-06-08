package pk.ni.pasir_wasko_klaudiusz.controller;

import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import pk.ni.pasir_wasko_klaudiusz.dto.BalanceDto;
import pk.ni.pasir_wasko_klaudiusz.dto.TransactionDTO;
import pk.ni.pasir_wasko_klaudiusz.model.Transaction;
import pk.ni.pasir_wasko_klaudiusz.service.TransactionService;

import java.util.List;

@Controller
public class TransactionGraphQLController {

    private final TransactionService transactionService;

    public TransactionGraphQLController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @QueryMapping
    public List<Transaction> transactions() {
        return transactionService.getAllTransactions();
    }

    @MutationMapping
    public Transaction addTransaction(@Valid @Argument TransactionDTO transactionDTO) {
        return transactionService.createTransaction(transactionDTO);
    }

    @MutationMapping
    public Transaction updateTransaction(@Argument Long id, @Valid @Argument TransactionDTO transactionDTO) {
        return transactionService.updateTransaction(id, transactionDTO);
    }

    @MutationMapping
    public Boolean deleteTransaction(@Argument Long id) {
        transactionService.deleteTransaction(id);
        return true;
    }
    @QueryMapping
    public BalanceDto userBalance(@Argument Double days) {
        pk.ni.pasir_wasko_klaudiusz.model.User user = transactionService.getCurrentUser();
        return transactionService.getUserBalance(user, days);
    }
}