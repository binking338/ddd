package org.ddd.domain.repo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author qiaohe
 * @date 2024/3/9
 */
@RequiredArgsConstructor
@Slf4j
public class JpaPersistListenerManager implements PersistListenerManager {
    private final List<AbstractJpaPersistListener> persistListeners;

    private Map<Class, List<AbstractJpaPersistListener>> persistListenersMap;

    private void init() {
        if (persistListenersMap == null) {
            synchronized (this) {
                persistListenersMap = new HashMap<>();
                if (persistListenersMap == null) {
                    persistListenersMap = new java.util.HashMap<Class, List<AbstractJpaPersistListener>>();
                    persistListeners.sort((a, b) ->
                            a.getClass().getAnnotation(Order.class).value() - b.getClass().getAnnotation(Order.class).value()
                    );
                    for (AbstractJpaPersistListener persistListener : persistListeners) {
                        if (!persistListenersMap.containsKey(persistListener.forEntityClass())) {
                            persistListenersMap.put(persistListener.forEntityClass(), new java.util.ArrayList<AbstractJpaPersistListener>());
                        }
                        List<AbstractJpaPersistListener> persistListenerList = persistListenersMap.get(persistListener.forEntityClass());
                        persistListenerList.add(persistListener);
                    }
                }
            }
        }
    }

    /**
     * onCreate & onUpdate
     * @param entity
     * @param <Entity>
     */
    @Override
    public <Entity> void onPersist(Entity entity) {
        init();
        List<AbstractJpaPersistListener> listeners = persistListenersMap.get(entity.getClass());
        if (listeners != null) {
            for (AbstractJpaPersistListener listener :
                    listeners) {
                try {
                    listener.onPersist(entity);
                } catch (Exception ex){
                    log.error("onPersist 异常", ex);
                }
            }
        }
    }

    @Override
    public <Entity> void onCreate(Entity entity) {
        init();
        List<AbstractJpaPersistListener> listeners = persistListenersMap.get(entity.getClass());
        if (listeners != null) {
            for (AbstractJpaPersistListener listener :
                    listeners) {
                try {
                    listener.onCreate(entity);
                } catch (Exception ex){
                    log.error("onCreate 异常", ex);
                }
            }
        }
    }

    @Override
    public <Entity> void onUpdate(Entity entity) {
        init();
        List<AbstractJpaPersistListener> listeners = persistListenersMap.get(entity.getClass());
        if (listeners != null) {
            for (AbstractJpaPersistListener listener :
                    listeners) {
                try {
                    listener.onUpdate(entity);
                } catch (Exception ex){
                    log.error("onUpdate 异常", ex);
                }
            }
        }

    }

    @Override
    public <Entity> void onDelete(Entity entity) {
        init();
        List<AbstractJpaPersistListener> listeners = persistListenersMap.get(entity.getClass());
        if (listeners != null) {
            for (AbstractJpaPersistListener listener :
                    listeners) {
                try {
                    listener.onDelete(entity);
                } catch (Exception ex){
                    log.error("onDelete 异常", ex);
                }
            }
        }

    }
}
