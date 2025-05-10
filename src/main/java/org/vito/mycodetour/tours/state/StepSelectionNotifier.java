package org.vito.mycodetour.tours.state;

import com.intellij.util.messages.Topic;
import org.vito.mycodetour.tours.domain.Step;

/**
* @author vito
* Created on 2025/1/1
 */
public interface StepSelectionNotifier {

   Topic<StepSelectionNotifier> TOPIC = Topic.create("Select Provided Step", StepSelectionNotifier.class);

   void selectStep(Step step);
}
