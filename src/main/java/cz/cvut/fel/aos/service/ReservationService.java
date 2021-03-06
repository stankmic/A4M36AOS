package cz.cvut.fel.aos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.fel.aos.dao.GenericEntityDao;
import cz.cvut.fel.aos.entities.ReservationEntity;
import cz.cvut.fel.aos.entities.ReservationState;
import cz.cvut.fel.aos.exceptions.InvalidReservationOperationException;
import cz.cvut.fel.aos.printclient.PrintingService_Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static cz.cvut.fel.aos.entities.ReservationState.*;

@Transactional
@Slf4j
public class ReservationService extends GenericService<ReservationEntity> {

    private final ObjectMapper objectMapper;
    private final String printServicePath;

    public ReservationService(GenericEntityDao<ReservationEntity> entityDao, ObjectMapper objectMapper, String printServicePath) {
        super(entityDao);
        this.objectMapper = objectMapper;
        this.printServicePath = printServicePath;
    }

    public ReservationEntity getWithPassword(int id, String password) {
        ReservationEntity e = this.get(id);
        checkPassword(e, password);
        return e;
    }

    public void create(ReservationEntity reservationEntity) {
        int reservedSeats = reservationEntity.getFlight().getReservations().stream().mapToInt(ReservationEntity::getSeats).sum();
        int seatsAvailable = reservationEntity.getFlight().getSeats() - reservedSeats;
        if (seatsAvailable < reservationEntity.getSeats()) {
            throw new InvalidReservationOperationException();
        }
        reservationEntity.setState(ReservationState.NEW);
        reservationEntity.setCreated(ZonedDateTime.now());
        entityDao.create(reservationEntity);
    }

    public void updateWithPassword(int id, ReservationEntity reservationEntity, String password) {
        ReservationEntity original = get(id);
        checkPassword(original, password);
        if (reservationEntity.getState() == CANCELLED) {
            original.setState(CANCELLED);
        }
        entityDao.update(original);
    }

    public void pay(int id) {
        ReservationEntity original = get(id);
        original.setState(PAID);
        entityDao.update(original);
    }

    public void mail(int id, String email) {
        /*RestTemplate restTemplate = new RestTemplate();
        try {
            restTemplate.exchange(
                    printServicePath,
                    HttpMethod.POST,
                    new HttpEntity<String>(objectMapper.writeValueAsString(get(id))),
                    String.class
            );
        } catch (Exception e) {
            log.error("Failed to connect to REST printing service.", e);
        }*/
        PrintingService_Service printingService_service = new PrintingService_Service();
        try {
            printingService_service.getPrintingServicePort().print(objectMapper.writeValueAsString(get(id)));
        } catch (Exception e) {
            log.error("Failed to connect to WS printing service.", e);
        }
    }

    @Override
    public void delete(int id) {
        ReservationEntity entity = get(id);
        if (entity.getState() == NEW) {
            super.delete(id);
        } else {
            throw new InvalidReservationOperationException();
        }
    }

    private void checkPassword(ReservationEntity e, String password) {
        if (!e.getPassword().equals(password)) {
            throw new InvalidReservationOperationException();
        }
    }
}
