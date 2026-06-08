package pk.ni.pasir_wasko_klaudiusz.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BalanceDto {
    private double totalIncome;
    private double totalExpense;
    private double balance;
}