package store.one3d.production;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PrinterRepository extends JpaRepository<Printer, UUID> {

    List<Printer> findAllByOrderByName();
}
