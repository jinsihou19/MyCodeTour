package org.vito.mycodetour.tours.state;

import com.intellij.util.messages.Topic;
import org.vito.mycodetour.tours.domain.Tour;

/**
* @author vito
* Created on 2025/1/1
 */
public interface TourUpdateNotifier {

   Topic<TourUpdateNotifier> TOPIC = Topic.create("Tour UI Update", TourUpdateNotifier.class);

   void tourUpdated(Tour tour);
}
