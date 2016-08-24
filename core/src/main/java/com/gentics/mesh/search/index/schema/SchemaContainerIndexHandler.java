package com.gentics.mesh.search.index.schema;

import java.util.Collections;
import java.util.Set;

import javax.inject.Inject;

import com.gentics.mesh.cli.BootstrapInitializer;
import com.gentics.mesh.context.InternalActionContext;
import com.gentics.mesh.core.data.root.RootVertex;
import com.gentics.mesh.core.data.schema.SchemaContainer;
import com.gentics.mesh.core.data.search.SearchQueueEntry;
import com.gentics.mesh.graphdb.spi.Database;
import com.gentics.mesh.search.IndexHandlerRegistry;
import com.gentics.mesh.search.SearchProvider;
import com.gentics.mesh.search.index.AbstractIndexHandler;

public class SchemaContainerIndexHandler extends AbstractIndexHandler<SchemaContainer> {

	private static SchemaContainerIndexHandler instance;

	private final static Set<String> indices = Collections.singleton("schema_container");

	private SchemaTransformator transformator = new SchemaTransformator();

	private BootstrapInitializer boot;

	@Inject
	public SchemaContainerIndexHandler(BootstrapInitializer boot, SearchProvider searchProvider, Database db, IndexHandlerRegistry registry) {
		super(searchProvider, db, registry);
		this.boot = boot;
		instance = this;
	}

	public SchemaTransformator getTransformator() {
		return transformator;
	}

	public static SchemaContainerIndexHandler getInstance() {
		return instance;
	}

	@Override
	protected String getIndex(SearchQueueEntry entry) {
		return "schema_container";
	}

	@Override
	public Set<String> getIndices() {
		return indices;
	}

	@Override
	public Set<String> getAffectedIndices(InternalActionContext ac) {
		return indices;
	}

	@Override
	protected String getType() {
		return "schemaContainer";
	}

	@Override
	public String getKey() {
		return SchemaContainer.TYPE;
	}

	@Override
	protected RootVertex<SchemaContainer> getRootVertex() {
		return boot.meshRoot().getSchemaContainerRoot();
	}

}