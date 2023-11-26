package  org.ddd.domain.event;

/**
 * @author qiaohe
 * @date 2023/9/9
 */
public interface EventRecordRepository {
    public EventRecord create();
    public EventRecord save(EventRecord event);
}
