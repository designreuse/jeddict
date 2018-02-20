/**
 * Copyright 2013-2018 the original author or authors from the Jeddict project (https://jeddict.github.io/).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.github.jeddict.db.modeler.initializer;

import io.github.jeddict.db.modeler.spec.DBColumn;
import io.github.jeddict.db.modeler.spec.DBForeignKey;
import io.github.jeddict.db.modeler.spec.DBPrimaryKey;
import io.github.jeddict.db.modeler.spec.DBSchema;
import io.github.jeddict.db.modeler.spec.DBTable;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import io.github.jeddict.db.modeler.widget.ColumnWidget;
import io.github.jeddict.db.modeler.widget.ForeignKeyWidget;
import io.github.jeddict.db.modeler.widget.ReferenceFlowWidget;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import io.github.jeddict.db.modeler.widget.TableWidget;
import io.github.jeddict.relation.mapper.initializer.RelationMapperUtil;
import io.github.jeddict.jpa.spec.extend.FlowNode;
import org.netbeans.modeler.config.document.IModelerDocument;
import org.netbeans.modeler.config.document.ModelerDocumentFactory;
import org.netbeans.modeler.core.ModelerFile;
import org.netbeans.modeler.core.NBModelerUtil;
import static org.netbeans.modeler.core.NBModelerUtil.getAutoGeneratedStringId;
import org.netbeans.modeler.core.exception.ModelerException;
import org.netbeans.modeler.core.exception.ProcessInterruptedException;
import org.netbeans.modeler.specification.model.document.core.IBaseElement;
import org.netbeans.modeler.specification.model.document.core.IFlowNode;
import org.netbeans.modeler.specification.model.document.widget.IBaseElementWidget;
import org.netbeans.modeler.specification.model.util.IModelerUtil;
import org.netbeans.modeler.widget.edge.IEdgeWidget;
import org.netbeans.modeler.widget.edge.info.EdgeWidgetInfo;
import org.netbeans.modeler.widget.node.INodeWidget;
import org.netbeans.modeler.widget.node.info.NodeWidgetInfo;
import org.netbeans.modeler.widget.node.vmd.PNodeWidget;
import org.netbeans.modules.db.explorer.DatabaseConnection;
import org.netbeans.modules.db.metadata.model.api.Column;
import org.netbeans.modules.db.metadata.model.api.ForeignKey;
import org.netbeans.modules.db.metadata.model.api.ForeignKeyColumn;
import org.netbeans.modules.db.metadata.model.api.Index;
import org.netbeans.modules.db.metadata.model.api.IndexColumn;
import org.netbeans.modules.db.metadata.model.api.Schema;
import org.netbeans.modules.db.metadata.model.api.Table;
import org.openide.nodes.Node;

public class DBModelerUtil implements IModelerUtil<DBModelerScene> {

    @Override
    public void init() {
        RelationMapperUtil relationMapperUtil = new RelationMapperUtil();//use icon from DBModelerUtil
        relationMapperUtil.init();
    }

    @Override
    public void loadModelerFile(ModelerFile file) throws ProcessInterruptedException {
        DBModelerScene scene = (DBModelerScene) file.getModelerScene();
        scene.startSceneGeneration();

        DBSchema dbSchema = getDBSchema(file);
        scene.setBaseElementSpec(dbSchema);
    
        dbSchema.getTables().forEach(table -> loadTable(scene, table));
        loadFlowEdge(scene);
        
        scene.autoLayout();
        scene.commitSceneGeneration();
    }

      private void loadTable(DBModelerScene scene, IFlowNode flowElement) {
        IModelerDocument document = null;
        ModelerDocumentFactory modelerDocumentFactory = scene.getModelerFile().getModelerDiagramModel().getModelerDocumentFactory();
        if (flowElement instanceof FlowNode) {
            FlowNode flowNode = (FlowNode) flowElement;

            try {
                document = modelerDocumentFactory.getModelerDocument(flowElement);
            } catch (ModelerException ex) {
                scene.getModelerFile().handleException(ex);
            }

            NodeWidgetInfo nodeWidgetInfo = new NodeWidgetInfo(document, new Point(0, 0));
            nodeWidgetInfo.setId(flowElement.getId());
            nodeWidgetInfo.setName(flowElement.getName());
            nodeWidgetInfo.setExist(Boolean.TRUE);
            nodeWidgetInfo.setBaseElementSpec(flowElement);
            INodeWidget nodeWidget = scene.createNodeWidget(nodeWidgetInfo);
            if (flowElement.getName() != null) {
                nodeWidget.setLabel(flowElement.getName());
            }
            if (flowNode.isMinimized()) {
                ((PNodeWidget) nodeWidget).setMinimized(true);
            }
            if (flowElement instanceof DBTable) {
                DBTable table = (DBTable) flowElement;
//                table.sortColumns();
                TableWidget tableWidget = (TableWidget) nodeWidget;
                if (table.getColumns() != null) {
                    table.getColumns().forEach(column -> {
                        if (column instanceof DBPrimaryKey) {
                            tableWidget.addPrimaryKeyColumn(column.getName(), column);
                        } else if (column instanceof DBForeignKey) {
                            tableWidget.addForeignKeyColumn(column.getName(), column);
                        } else {
                            tableWidget.addBasicColumn(column.getName(), column);
                        }
                    });
//                    tableWidget.sortAttributes();
                }
                scene.reinstallColorScheme(tableWidget);
            }

        }
    }

    private void loadFlowEdge(DBModelerScene scene) {
        scene.getBaseElements()
                .stream()
                .filter(baseElementWidget -> baseElementWidget instanceof TableWidget)
                .map(baseElementWidget -> (TableWidget)baseElementWidget)
                .forEach(tableWidget -> 
                    tableWidget.getForeignKeyWidgets()
                            .forEach(foreignKeyWidget -> loadEdge(scene, tableWidget, foreignKeyWidget))
                );
    }
    
      private void loadEdge(DBModelerScene scene, TableWidget sourceTableWidget, ForeignKeyWidget foreignKeyWidget) {
//       ForeignKey => Source
//       PrimaryKey => Target
        DBForeignKey sourceColumn = foreignKeyWidget.getBaseElementSpec();

        TableWidget targetTableWidget = (TableWidget) scene.getBaseElement(sourceColumn.getReferenceTable().getId());
        ColumnWidget targetColumnWidget = targetTableWidget.findColumnWidget(sourceColumn.getReferenceColumn().getId());

        EdgeWidgetInfo edgeInfo = new EdgeWidgetInfo(e ->  new ReferenceFlowWidget(scene, e));
        edgeInfo.setId(NBModelerUtil.getAutoGeneratedStringId());
        edgeInfo.setSource(sourceTableWidget.getNodeWidgetInfo().getId());
        edgeInfo.setTarget(targetTableWidget.getNodeWidgetInfo().getId());
        edgeInfo.setType("REFERENCE");
        IEdgeWidget edgeWidget = scene.createEdgeWidget(edgeInfo);

        scene.setEdgeWidgetSource(edgeInfo, edgeWidget.getSourcePinWidget(sourceTableWidget, targetTableWidget, foreignKeyWidget));
        scene.setEdgeWidgetTarget(edgeInfo, edgeWidget.getTargetPinWidget(sourceTableWidget, targetTableWidget, targetColumnWidget));
    }
          
    private DBSchema getDBSchema(ModelerFile file) {
        Node node = (Node) file.getAttribute(Node.class.getSimpleName());
        DatabaseConnection connection = node.getLookup().lookup(DatabaseConnection.class);
        Schema schema = (Schema) file.getAttribute(Schema.class.getSimpleName());
        boolean system = false;
        DBSchema dbSchema = null;
        if (schema != null) {
            dbSchema = new DBSchema(schema);
            Map<Column, DBColumn> referredColumns = new HashMap<>();
            Map<Column, DBTable> referredTables = new HashMap<>();
            List<DBForeignKey> referringColumns = new ArrayList<>();
            Map<String, Column> primaryColumns = new HashMap<>();

            Collection<Table> tables = schema.getTables();
            for (Table table : tables) {
                if (connection.isSeparateSystemTables() && ((!system) && table.isSystem())
                        || (system && (!table.isSystem()))) {
                    continue;
                }
                DBTable dbTable = new DBTable(table.getName(), table);
                dbTable.setId(getAutoGeneratedStringId());
                dbSchema.addTable(dbTable);
                
                //<ReferringColumn, ForeignKeyColumn>
                //ReferringColumn == self
                Map<Column, ForeignKeyColumn> fkColumnSet = new HashMap<>();
                for (ForeignKey foreignKey : table.getForeignKeys()) {
                    for (ForeignKeyColumn fkcolumn : foreignKey.getColumns()) {
                        fkColumnSet.put(fkcolumn.getReferringColumn(), fkcolumn);
                    }
                }
                
                for (Column column : table.getPrimaryKey().getColumns()) {
                    primaryColumns.put(column.getName(), column);
                }

                for (Column column : table.getColumns()) {
                    DBColumn dbColumn;
                    ForeignKeyColumn foreignKeyColumn = fkColumnSet.get(column);
                    if (foreignKeyColumn != null) {
                        dbColumn = new DBForeignKey(column.getName(), foreignKeyColumn);
                        referringColumns.add((DBForeignKey) dbColumn);
                    } else if(primaryColumns.containsKey(column.getName())){
                        dbColumn = new DBPrimaryKey(column.getName(), column);
                    } else {
                        dbColumn = new DBColumn(column.getName(), column);
                    }
                    dbColumn.setId(getAutoGeneratedStringId());
                    dbTable.addColumn(dbColumn);
                    
                    referredColumns.put(column, dbColumn);
                    referredTables.put(column, dbTable);
                }
            }

            referringColumns.forEach(referringColumn -> {
                referringColumn.setReferenceColumn(
                        referredColumns.get(referringColumn.getForeignKeyColumn().getReferredColumn())
                );
                referringColumn.setReferenceTable(
                        referredTables.get(referringColumn.getForeignKeyColumn().getReferredColumn())
                );
            });
        }
        return dbSchema;
    }

    @Override
    public void loadBaseElement(IBaseElementWidget parentConatiner, Map<IBaseElement, Rectangle> elements) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<IBaseElement> clone(List<IBaseElement> elements) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void saveModelerFile(ModelerFile file) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String getContent(ModelerFile file) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}