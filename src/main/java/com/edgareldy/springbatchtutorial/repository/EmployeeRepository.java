package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Employee}, used by
 * {@code TimesheetItemProcessor} to resolve a CSV row's email column to an
 * {@code employee_id} during {@code importTimesheets}.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);
}
