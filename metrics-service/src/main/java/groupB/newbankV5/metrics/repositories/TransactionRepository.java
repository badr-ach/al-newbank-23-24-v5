package groupB.newbankV5.metrics.repositories;

import groupB.newbankV5.metrics.entities.Transaction;
import groupB.newbankV5.metrics.entities.TransactionEnvelop;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TransactionRepository extends MongoRepository<TransactionEnvelop, String> {






}
