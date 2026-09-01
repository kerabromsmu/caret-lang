package caretlang;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

final class ContractRelationsTest {
    @Test
    void provesOnlyIdentityDerivationAbsenceAndDeclaredSequenceVariance() {
        UserContract root = new UserContract(List.of());
        UserContract left = new UserContract(List.of(root));
        UserContract right = new UserContract(List.of(root));
        UserContract diamond = new UserContract(List.of(left, right));

        assertTrue(ContractRelations.implies(diamond, left));
        assertTrue(ContractRelations.implies(diamond, root));
        assertFalse(ContractRelations.implies(left, right));

        ContractDescriptor nullableNumber = new ModifiedContract(BuiltinContract.NUMBER, true, false);
        ContractDescriptor absentNumber = new ModifiedContract(BuiltinContract.NUMBER, true, true);
        assertTrue(ContractRelations.implies(BuiltinContract.NUMBER, nullableNumber));
        assertTrue(ContractRelations.implies(nullableNumber, absentNumber));
        assertFalse(ContractRelations.implies(nullableNumber,
                new ModifiedContract(BuiltinContract.NUMBER, false, true)));

        ContractDescriptor numbers = new ParameterizedContract(
                BuiltinContract.SEQUENCE, List.of(BuiltinContract.NUMBER));
        ContractDescriptor anyValues = new ParameterizedContract(
                BuiltinContract.SEQUENCE, List.of(BuiltinContract.ANY));
        assertTrue(ContractRelations.implies(numbers, anyValues));
        assertFalse(ContractRelations.implies(anyValues, numbers));
    }
}
