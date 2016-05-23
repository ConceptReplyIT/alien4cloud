package alien4cloud.topology.task;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.json.deserializer.TaskIndexedInheritableToscaElementDeserializer;
import alien4cloud.model.components.IndexedInheritableToscaElement;
import alien4cloud.utils.jackson.ConditionalAttributes;
import alien4cloud.utils.jackson.ConditionalOnAttribute;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 *
 * Represent one task to do to have a deployable topology
 *
 * @author 'Igor Ngouagna'
 *
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TopologyTask extends AbstractTask {
    // Name of the node template that needs to be fixed.
    private String nodeTemplateName;
    // related component
    @ConditionalOnAttribute(ConditionalAttributes.REST)
    @JsonDeserialize(using = TaskIndexedInheritableToscaElementDeserializer.class)
    private IndexedInheritableToscaElement component;
}
