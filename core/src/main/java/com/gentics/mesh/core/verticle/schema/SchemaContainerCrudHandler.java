package com.gentics.mesh.core.verticle.schema;

import static com.gentics.mesh.core.data.relationship.GraphPermission.READ_PERM;
import static com.gentics.mesh.core.data.relationship.GraphPermission.UPDATE_PERM;
import static com.gentics.mesh.core.rest.common.GenericMessageResponse.message;
import static com.gentics.mesh.core.rest.error.Errors.error;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.OK;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.Project;
import com.gentics.mesh.core.data.relationship.GraphPermission;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.schema.handler.SchemaComparator;
import com.gentics.mesh.core.rest.schema.Schema;
import com.gentics.mesh.core.rest.schema.change.impl.SchemaChangesListModel;
import com.gentics.mesh.core.rest.schema.impl.SchemaModel;
import com.gentics.mesh.core.verticle.handler.AbstractCrudHandler;
import com.gentics.mesh.core.verticle.handler.HandlerUtilities;
import com.gentics.mesh.json.JsonUtil;

import rx.Single;

@Component
public class SchemaContainerCrudHandler extends AbstractCrudHandler<SchemaContainer, Schema> {

	@Autowired
	private SchemaComparator comparator;

	@Override
	public RootVertex<SchemaContainer> getRootVertex(InternalActionContext ac) {
		return boot.schemaContainerRoot();
	}

	@Override
	public void handleDelete(InternalActionContext ac, String uuid) {
		validateParameter(uuid, "uuid");
		HandlerUtilities.deleteElement(ac, () -> boot.schemaContainerRoot(), uuid, "schema_deleted");
	}

	@Override
	public void handleUpdate(InternalActionContext ac, String uuid) {
		validateParameter(uuid, "uuid");
		db.asyncNoTrxExperimental(() -> {
			RootVertex<SchemaContainer> root = getRootVertex(ac);
			return root.loadObjectByUuid(ac, uuid, UPDATE_PERM).flatMap(element -> {
				try {
					Schema requestModel = JsonUtil.readValue(ac.getBodyAsString(), SchemaModel.class);
					SchemaChangesListModel model = new SchemaChangesListModel();
					model.getChanges().addAll(SchemaComparator.getIntance().diff(element.getLatestVersion().getSchema(), requestModel));
					String schemaName = element.getName();
					if (model.getChanges().isEmpty()) {
						return Single.just(message(ac, "schema_update_no_difference_detected", schemaName));
					} else {
						return element.getLatestVersion().applyChanges(ac, model).flatMap(e -> {
							return Single.just(message(ac, "migration_invoked", schemaName));
						});
					}
				} catch (Exception e) {
					return Single.error(e);
				}
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleDiff(InternalActionContext ac, String uuid) {
		db.asyncNoTrxExperimental(() -> {
			Single<SchemaContainer> obsSchema = getRootVertex(ac).loadObjectByUuid(ac, uuid, READ_PERM);
			Schema requestModel = JsonUtil.readValue(ac.getBodyAsString(), SchemaModel.class);
			return obsSchema.flatMap(schema -> schema.getLatestVersion().diff(ac, comparator, requestModel));
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleReadProjectList(InternalActionContext ac) {
		HandlerUtilities.readElementList(ac, () -> ac.getProject().getSchemaContainerRoot());
	}

	public void handleAddSchemaToProject(InternalActionContext ac, String schemaUuid) {
		validateParameter(schemaUuid, "schemaUuid");

		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			String projectUuid = project.getUuid();
			Single<SchemaContainer> obsSchema = getRootVertex(ac).loadObjectByUuid(ac, schemaUuid, READ_PERM);
			Single<Boolean> obsPerm = ac.getUser().hasPermissionAsync(ac, project.getImpl(), GraphPermission.UPDATE_PERM);

			return Single.zip(obsPerm, obsSchema, (perm, schema) -> {
				if (!perm.booleanValue()) {
					throw error(FORBIDDEN, "error_missing_perm", projectUuid);
				}
				return db.tx(() -> {
					//TODO SQB ?
					project.getSchemaContainerRoot().addSchemaContainer(schema);
					return schema.transformToRest(ac, 0);
				});
			}).flatMap(x -> x);

		}).subscribe(model -> ac.respond(model, OK), ac::fail);

	}

	public void handleRemoveSchemaFromProject(InternalActionContext ac, String schemaUuid) {
		validateParameter(schemaUuid, "schemaUuid");

		db.asyncNoTrxExperimental(() -> {
			Project project = ac.getProject();
			String projectUuid = project.getUuid();
			Single<SchemaContainer> obsSchema = boot.schemaContainerRoot().loadObjectByUuid(ac, schemaUuid, READ_PERM);
			Single<Boolean> obsPerm = ac.getUser().hasPermissionAsync(ac, project.getImpl(), GraphPermission.UPDATE_PERM);

			// TODO check whether schema is assigned to project

			return Single.zip(obsPerm, obsSchema, (perm, schema) -> {
				if (!perm.booleanValue()) {
					throw error(FORBIDDEN, "error_missing_perm", projectUuid);
				}
				return db.tx(() -> {
					project.getSchemaContainerRoot().removeSchemaContainer(schema);
					return schema.transformToRest(ac, 0);
				});
			}).flatMap(x -> x);
		}).subscribe(model -> ac.respond(model, OK), ac::fail);
	}

	public void handleGetSchemaChanges(InternalActionContext ac) {
		// TODO Auto-generated method stub

	}

	public void handleApplySchemaChanges(InternalActionContext ac, String schemaUuid) {
		validateParameter(schemaUuid, "schemaUuid");

		db.asyncNoTrxExperimental(() -> {
			Single<SchemaContainer> obsSchema = boot.schemaContainerRoot().loadObjectByUuid(ac, schemaUuid, UPDATE_PERM);
			return obsSchema.flatMap(schema -> {
				return schema.getLatestVersion().applyChanges(ac);
			});
		}).subscribe(model -> ac.respond(model, OK), ac::fail);

	}

}
