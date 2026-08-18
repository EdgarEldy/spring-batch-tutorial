package com.edgareldy.springbatchtutorial.repository;

import com.edgareldy.springbatchtutorial.entity.Employee;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Spring Data JPA repository for {@link Employee}, used by
 * {@code TimesheetItemProcessor} to resolve a CSV row's email column to an
 * {@code employee_id} during {@code importTimesheets}, and by
 * {@code EmployeePartitioner} to size {@code calculatePayslips}'s id-range
 * partitions via {@link #findMinId()}/{@link #findMaxId()} without ever
 * loading the employee rows themselves.
 * <p>
 * Created by Edgar Muhamyangabo on 8/17/26
 * Author : Edgar Muhamyangabo
 * Date : 8/17/26
 * Project : spring-batch-tutorial
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    /** Null when {@code employees} is empty, handled by {@code EmployeePartitioner}. */
    @Query("select min(e.id) from Employee e")
    Long findMinId();

    /** Null when {@code employees} is empty, handled by {@code EmployeePartitioner}. */
    @Query("select max(e.id) from Employee e")
    Long findMaxId();
}
