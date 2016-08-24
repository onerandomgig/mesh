package com.gentics.mesh.core.data.node.field.impl;

import static com.gentics.mesh.core.data.relationship.GraphRelationships.HAS_FIELD;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.GraphFieldContainer;
import com.gentics.mesh.core.data.diff.FieldChangeTypes;
import com.gentics.mesh.core.data.diff.FieldContainerChange;
import com.gentics.mesh.core.data.generic.MeshEdgeImpl;
import com.gentics.mesh.core.data.node.Micronode;
import com.gentics.mesh.core.data.node.field.GraphField;
import com.gentics.mesh.core.data.node.field.nesting.MicronodeGraphField;
import com.gentics.mesh.core.data.node.impl.MicronodeImpl;
import com.gentics.mesh.core.rest.node.field.Field;
import com.gentics.mesh.core.rest.node.field.MicronodeField;
import com.gentics.mesh.core.rest.schema.FieldSchema;
import com.gentics.mesh.core.rest.schema.Microschema;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.util.CompareUtils;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import rx.Single;

/**
 * See {@link MicronodeGraphField}
 */
public class MicronodeGraphFieldImpl extends MeshEdgeImpl implements MicronodeGraphField {

	private static final Logger log = LoggerFactory.getLogger(MicronodeGraphFieldImpl.class);

	public static void init(Database db) {
		db.addEdgeType(MicronodeGraphFieldImpl.class.getSimpleName());
		db.addEdgeType(HAS_FIELD, MicronodeGraphFieldImpl.class);
	}

	@Override
	public void setFieldKey(String key) {
		setProperty(GraphField.FIELD_KEY_PROPERTY_KEY, key);
	}

	@Override
	public String getFieldKey() {
		return getProperty(GraphField.FIELD_KEY_PROPERTY_KEY);
	}

	@Override
	public Micronode getMicronode() {
		return inV().has(MicronodeImpl.class).nextOrDefaultExplicit(MicronodeImpl.class, null);
	}

	@Override
	public Single<? extends Field> transformToRest(InternalActionContext ac, String fieldKey, List<String> languageTags, int level) {
		Micronode micronode = getMicronode();
		if (micronode == null) {
			// TODO is this correct?
			throw error(BAD_REQUEST, "error_name_must_be_set");
		} else {
			if (languageTags != null) {
				return micronode.transformToRestSync(ac, level, languageTags.toArray(new String[languageTags.size()]));
			} else {
				return micronode.transformToRestSync(ac, level);
			}
		}
	}

	@Override
	public void removeField(GraphFieldContainer container) {
		Micronode micronode = getMicronode();
		// 1. Remove the edge
		remove();
		if (micronode != null) {
			// Remove the micronode if this was the last edge to the micronode
			if (micronode.getImpl().in(HAS_FIELD).count() == 0) {
				micronode.delete(null);
			}
		}
	}

	@Override
	public GraphField cloneTo(GraphFieldContainer container) {
		Micronode micronode = getMicronode();

		MicronodeGraphField field = getGraph().addFramedEdge(container.getImpl(), micronode.getImpl(), HAS_FIELD, MicronodeGraphFieldImpl.class);
		field.setFieldKey(getFieldKey());
		return field;
	}

	@Override
	public void validate() {
		getMicronode().validate();
	}

	/**
	 * Override the default implementation since micronode graph fields are container for other fields. We also want to catch the nested fields.
	 * 
	 * @param field
	 * @return
	 */
	@Override
	public List<FieldContainerChange> compareTo(Object field) {
		if (field instanceof MicronodeGraphField) {
			Micronode micronodeA = getMicronode();
			Micronode micronodeB = ((MicronodeGraphField) field).getMicronode();
			List<FieldContainerChange> changes = micronodeA.compareTo(micronodeB);
			// Update the detected changes and prepend the fieldkey of the micronode in order to be able to identify nested changes more easy.
			changes.stream().forEach(c -> {
				c.setFieldCoordinates(getFieldKey() + "." + c.getFieldKey());
				// Reset the field key
				c.setFieldKey(getFieldKey());
			});
			return changes;
		}
		if (field instanceof MicronodeField) {
			List<FieldContainerChange> changes = new ArrayList<>();
			Micronode micronodeA = getMicronode();
			MicronodeField micronodeB = ((MicronodeField) field);
			// Load each field using the field schema 
			Microschema schema = micronodeA.getSchemaContainerVersion().getSchema();
			for (FieldSchema fieldSchema : schema.getFields()) {
				GraphField graphField = micronodeA.getField(fieldSchema);
				try {
					Field nestedRestField = micronodeB.getFields().getField(fieldSchema.getName(), fieldSchema);
					// If possible compare the graph field with the rest field 
					if (graphField != null && graphField.equals(nestedRestField)) {
						continue;
					}
					// Field is not part of the request and has not been set to null. Skip it.
					if (nestedRestField == null && !micronodeB.getFields().hasField(fieldSchema.getName())) {
						continue;
					}
					if (!CompareUtils.equals(graphField, nestedRestField)) {
						FieldContainerChange change = new FieldContainerChange(getFieldKey(), FieldChangeTypes.UPDATED);
						// Set the micronode specific field coordinates
						change.setFieldCoordinates(getFieldKey() + "." + fieldSchema.getName());
						changes.add(change);

					}
				} catch (Exception e) {
					//TODO i18n
					throw error(INTERNAL_SERVER_ERROR, "Can't load rest field {" + fieldSchema.getName() + "} from micronode {" + getFieldKey() + "}",
							e);
				}
			}
			return changes;

		}
		return Collections.emptyList();

	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof MicronodeGraphField) {
			Micronode micronodeA = getMicronode();
			Micronode micronodeB = ((MicronodeGraphField) obj).getMicronode();
			return CompareUtils.equals(micronodeA, micronodeB);
		}
		if (obj instanceof MicronodeField) {
			Micronode micronodeA = getMicronode();
			MicronodeField micronodeB = ((MicronodeField) obj);

			// Load each field using the field schema 
			Microschema schema = micronodeA.getSchemaContainerVersion().getSchema();
			for (FieldSchema fieldSchema : schema.getFields()) {
				GraphField graphField = micronodeA.getField(fieldSchema);
				try {
					Field nestedRestField = micronodeB.getFields().getField(fieldSchema.getName(), fieldSchema);
					// If possible compare the graph field with the rest field 
					if (graphField != null && graphField.equals(nestedRestField)) {
						continue;
					}
					if (!CompareUtils.equals(graphField, nestedRestField)) {
						return false;
					}
				} catch (Exception e) {
					log.error("Could not load rest field", e);
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}
}