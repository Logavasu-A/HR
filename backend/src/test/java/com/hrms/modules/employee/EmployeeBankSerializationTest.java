package com.hrms.modules.employee;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.modules.admin.model.AppUser;
import com.hrms.modules.employee.model.Employee;
import com.hrms.modules.employee.service.EmployeeBankService;
import com.hrms.modules.payroll.model.SalarySlip;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class EmployeeBankSerializationTest {
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();

    @Test
    void ordinaryEmployeePayloadCannotCarryBankFields() throws Exception {
        var employee = json.readValue("""
                {
                    "firstName": "Jordan",
                    "bankName": "Ignored bank",
                    "bankAccountNo": "IGNORED-ACCOUNT"
                }
                """, Employee.class);

        var response = json.valueToTree(employee);
        assertThat(response.path("firstName").asText()).isEqualTo("Jordan");
        assertThat(response.has("bankName")).isFalse();
        assertThat(response.has("bankAccountNo")).isFalse();
    }

    @Test
    void nestedEmployeePayloadsUseTheSameRedactedRepresentation() {
        var employee = new Employee();
        employee.setFirstName("Jordan");
        var user = new AppUser();
        user.setEmployee(employee);
        var slip = new SalarySlip();
        slip.setEmployee(employee);

        for (var parent : new Object[]{user, slip}) {
            var nested = json.valueToTree(parent).path("employee");
            assertThat(nested.path("firstName").asText()).isEqualTo("Jordan");
            assertThat(nested.has("bankName")).isFalse();
            assertThat(nested.has("bankAccountNo")).isFalse();
        }
    }

    @Test
    void dedicatedResponseHasOnlyTheTwoBankFields() {
        var details = json.valueToTree(new EmployeeBankService.Details("Test bank", "TEST-ACCOUNT"));
        assertThat(details.size()).isEqualTo(2);
        assertThat(details.path("bankName").asText()).isEqualTo("Test bank");
        assertThat(details.path("bankAccountNo").asText()).isEqualTo("TEST-ACCOUNT");
    }
}
