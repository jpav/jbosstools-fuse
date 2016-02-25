/******************************************************************************* 
 * Copyright (c) 2015 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/ 

package org.fusesource.ide.camel.editor;

import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointsListener;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.gef.ContextMenuProvider;
import org.eclipse.gef.EditDomain;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.KeyHandler;
import org.eclipse.gef.RootEditPart;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.editparts.AbstractEditPart;
import org.eclipse.gef.editparts.ScalableRootEditPart;
import org.eclipse.gef.editparts.ZoomManager;
import org.eclipse.gef.ui.actions.ActionRegistry;
import org.eclipse.gef.ui.palette.FlyoutPaletteComposite;
import org.eclipse.gef.ui.parts.GraphicalEditorWithFlyoutPalette;
import org.eclipse.graphiti.dt.IDiagramTypeProvider;
import org.eclipse.graphiti.features.IFeatureProvider;
import org.eclipse.graphiti.mm.pictograms.Diagram;
import org.eclipse.graphiti.mm.pictograms.PictogramElement;
import org.eclipse.graphiti.ui.editor.DiagramBehavior;
import org.eclipse.graphiti.ui.editor.DiagramEditor;
import org.eclipse.graphiti.ui.editor.IDiagramEditorInput;
import org.eclipse.graphiti.ui.internal.parts.ContainerShapeEditPart;
import org.eclipse.graphiti.ui.internal.util.gef.ScalableRootEditPartAnimated;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.views.contentoutline.IContentOutlinePage;
import org.fusesource.ide.camel.editor.behaviours.CamelDiagramBehaviour;
import org.fusesource.ide.camel.editor.commands.DiagramOperations;
import org.fusesource.ide.camel.editor.commands.ImportCamelContextElementsCommand;
import org.fusesource.ide.camel.editor.internal.CamelDesignEditorFlyoutPaletteComposite;
import org.fusesource.ide.camel.editor.internal.CamelEditorUIActivator;
import org.fusesource.ide.camel.editor.outline.CamelModelOutlinePage;
import org.fusesource.ide.camel.editor.provider.CamelEditorContextMenuProvider;
import org.fusesource.ide.camel.editor.utils.DiagramUtils;
import org.fusesource.ide.camel.editor.utils.INodeViewer;
import org.fusesource.ide.camel.editor.utils.NodeUtils;
import org.fusesource.ide.camel.model.service.core.model.CamelContextElement;
import org.fusesource.ide.camel.model.service.core.model.CamelFile;
import org.fusesource.ide.camel.model.service.core.model.CamelModelElement;
import org.fusesource.ide.camel.model.service.core.model.ICamelModelListener;
import org.fusesource.ide.camel.validation.diagram.IFuseMarker;
import org.fusesource.ide.foundation.core.util.Objects;
import org.fusesource.ide.foundation.ui.io.CamelXMLEditorInput;
import org.fusesource.ide.foundation.ui.util.Selections;
import org.fusesource.ide.launcher.debug.model.CamelStackFrame;
import org.fusesource.ide.launcher.debug.model.CamelThread;
import org.fusesource.ide.launcher.debug.util.CamelDebugRegistry;
import org.fusesource.ide.launcher.debug.util.CamelDebugRegistryEntry;
import org.fusesource.ide.launcher.debug.util.ICamelDebugConstants;
import org.fusesource.ide.preferences.PreferenceManager;
import org.fusesource.ide.preferences.PreferencesConstants;

/*
 * @author lhein
 */
public class CamelDesignEditor extends DiagramEditor implements ISelectionListener, 
																INodeViewer, 
																ICamelModelListener, 
																IDebugEventSetListener, 
																IBreakpointsListener {
	
	private CamelEditor parent;
	private IProject workspaceProject;
	private CamelDiagramBehaviour camelDiagramBehaviour;
	private CamelDesignEditorFlyoutPaletteComposite paletteComposite;
	private CamelFile model;
	private CamelModelElement selectedContainer;
	private CamelModelElement highlightedNodeInDebugger;
	private AbstractEditPart selectedEditPart;
	private AbstractEditPart lastSelectedEditPart;
	private KeyHandler keyHandler;
	private CamelModelOutlinePage outlinePage;
	
	/**
	 * 
	 * @param parent
	 */
	public CamelDesignEditor(CamelEditor parent) {
		this.parent = parent;
		DebugPlugin.getDefault().addDebugEventListener(this);
		if (PlatformUI.getWorkbench().getActiveWorkbenchWindow() != null) {
			ISelectionService sel = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService();
			if (sel != null) sel.addSelectionListener(ICamelDebugConstants.DEBUG_VIEW_ID, this);			
		}
		DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#createDiagramBehavior()
	 */
	@Override
	protected DiagramBehavior createDiagramBehavior() {
		this.camelDiagramBehaviour = new CamelDiagramBehaviour(this);
		return this.camelDiagramBehaviour;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#getDiagramBehavior()
	 */
	@Override
	public CamelDiagramBehaviour getDiagramBehavior() {
		return this.camelDiagramBehaviour;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#dispose()
	 */
	@Override
	public void dispose() {
		getModel().removeModelListener(this);
		super.dispose();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#configureGraphicalViewer()
	 */
	@Override
	public void configureGraphicalViewer() {
		super.configureGraphicalViewer();
		GraphicalViewer viewer = getGraphicalViewer();
		ContextMenuProvider provider = new CamelEditorContextMenuProvider(this, this, viewer, getActionRegistry());
		viewer.setContextMenu(provider);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#setInput(org.eclipse.ui.IEditorInput)
	 */
	@Override
	protected void setInput(IEditorInput input) {
		super.setInput(input);
		
		if(getDiagramBehavior().getEditorInitializationError() == null){
			/**
	         * the following is needed because we miss the first debug events and the first breakpoint wouldn't be highlighted otherwise
	         */
	        try {
		        for (CamelDebugRegistryEntry entry : CamelDebugRegistry.getInstance().getEntries().values()) {
					IResource f = this.model.getResource();
		        	if (f.getFullPath().toFile().getPath().equals(asFileEditorInput(input).getFile().getFullPath().toFile().getPath())) {
		        		String endpointId = null;
		        		
		        		// first highlight the suspended node
		        		if (entry != null && entry.getDebugTarget() != null && entry.getDebugTarget().getDebugger() != null) {
			        		Set<String> ids = entry.getDebugTarget().getDebugger().getSuspendedBreakpointNodeIds();
			        		if (ids != null && ids.size()>0) {
			        			endpointId = ids.iterator().next();
			        		}
			        		highlightBreakpointNodeWithID(endpointId);
		        		}
		        	}
		        }
	        } catch (Exception ex) {
	        	CamelEditorUIActivator.pluginLog().logError(ex);
	        }
	        /**
	         * End of the highlighting code
	         */
	        
	        // setup grid visibility
	        setupGridVisibilityAsync();
	        
	        // update outline view
	        this.outlinePage = new CamelModelOutlinePage(this);
	        
			// clear Old Validation Markers,
			// they will be recalculated when opening the diagram
			try {
				asFileEditorInput(input).getFile().deleteMarkers(IFuseMarker.MARKER_TYPE, true, IResource.DEPTH_INFINITE);
			} catch (CoreException e) {
				CamelEditorUIActivator.pluginLog().logError(e);
			}

	        setSelectedContainer(getModel().findNode(parent.getCamelXMLInput().getSelectedContainerId()));        
//			getEditingDomain().getCommandStack().flush();
		}
		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#setFocus()
	 */
	@Override
	public void setFocus() {
		super.setFocus();
		if (getModel() != null) {
			DiagramOperations.updateDiagram(this);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#isDirty()
	 */
	@Override
	public boolean isDirty() {
		return parent.isDirty();
	}
	
    /**
     * Create a FlyoutPaletteComposite the will used to show a flyout palette
     * alongside the editor.
     * 
     * @param parent
     *            The parent composite hosting the FlyoutPaletteComposite.
     * @return a newly-created {@link FlyoutPaletteComposite}
     */
    @Override
	protected FlyoutPaletteComposite createPaletteComposite(Composite parent) {
        paletteComposite = new CamelDesignEditorFlyoutPaletteComposite(parent, SWT.NONE, getSite()
                .getPage(), getPaletteViewerProvider(), getPalettePreferences());
        paletteComposite.getFilter().addModifyListener(new ModifyListener() {
            @Override
            public void modifyText(ModifyEvent e) {
                getDiagramBehavior().refreshPalette();
            }
        });
        return paletteComposite;
    }
    
	/*
	 * (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#selectionChanged(org.eclipse.ui.IWorkbenchPart, org.eclipse.jface.viewers.ISelection)
	 */
	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		// If not the active editor, ignore selection changed.
		if (getSite().getPage().getActiveEditor() == this.parent && this.parent.getActiveEditor() == this) {
			updateActions(getSelectionActions());

			if (selection instanceof StructuredSelection) {
				StructuredSelection structuredSelection = (StructuredSelection) selection;
				Object firstElement = structuredSelection.getFirstElement();
				
				/** this handles selections in the outline view -> selects the node in diagram **/
				if (firstElement instanceof CamelModelElement) {
					CamelModelElement node = (CamelModelElement)firstElement;
					if (node != null) {
						if (node instanceof CamelContextElement == false) setSelectedNode(node);
					}
				
				/** this handles selections in the diagram -> selects node in outline view **/
				} else if (firstElement instanceof ContainerShapeEditPart) {
					CamelModelElement node = NodeUtils.toCamelElement(firstElement);
					if (node != null) {
						this.outlinePage.setOutlineSelection(node);
					}
					
				/** this highlights endpoints currently hitting a breakpoint **/
				} else if (firstElement instanceof CamelStackFrame) {
					CamelStackFrame stackFrame = (CamelStackFrame)firstElement;
					highlightBreakpointNodeWithID(stackFrame.getEndpointId());
				} else if (firstElement instanceof CamelThread) {
					CamelThread t = (CamelThread)firstElement;
					try {
						CamelStackFrame stackFrame = (CamelStackFrame)t.getTopStackFrame();
						if (stackFrame != null) {
							highlightBreakpointNodeWithID(stackFrame.getEndpointId());	
						}
					} catch (DebugException e) {
						CamelEditorUIActivator.pluginLog().logError(e);
					}
				}
			}
		}
	}
	
	/**
	 * @return the keyHandler
	 */
	public KeyHandler getKeyHandler() {
		return this.keyHandler;
	}
	
	public void initializeDiagram(Diagram diagram) {
	    // set the diagram on the container
		IDiagramTypeProvider diagramTypeProvider = getDiagramTypeProvider();
		if (diagramTypeProvider == null)
			return;
		
	    if (diagramTypeProvider.getDiagram() != diagram) {
	    	diagramTypeProvider.resourceReloaded(diagram);
	    }
	    
	    diagramTypeProvider.init(diagram, getDiagramBehavior());
		getDiagramBehavior().getRefreshBehavior().initRefresh();
	}
    
	/**
	 * @return the model
	 */
	public CamelFile getModel() {
		return this.model;
	}
	
	/**
	 * @param model the model to set
	 */
	public void setModel(CamelFile model) {
		this.model = model;
		if (model != null && model.getCamelFile() != null) {
			model.getCamelFile().addModelListener(this);
			model.getCamelFile().addModelListener(getParent().getGlobalConfigEditor());
			setSelectedContainer(model.getCamelContext());
		}
	}
	
    /**
	 * @return the workspaceProject
	 */
	public IProject getWorkspaceProject() {
		return this.workspaceProject;
	}
	
	/**
	 * @param workspaceProject the workspaceProject to set
	 */
	public void setWorkspaceProject(IProject workspaceProject) {
		this.workspaceProject = workspaceProject;
	}
	
	/**
	 * returns the filter string entered in palette search box
	 * 
	 * @return
	 */
	public String getPaletteFilter() {
        if (paletteComposite != null) {
            return paletteComposite.getFilter().getText();
        }
        return null;
    }
	
	/**
	 * 
	 * @param input
	 * @return
	 */
	public IFileEditorInput asFileEditorInput(IEditorInput input) {
		if (input instanceof IFileEditorInput) {
			return (IFileEditorInput) input;
		} else if (input instanceof CamelXMLEditorInput) {
			return new FileEditorInput((IFile)input.getAdapter(IFile.class));
		} else if (input instanceof IDiagramEditorInput) {
			org.eclipse.emf.common.util.URI uri = ((IDiagramEditorInput)input).getUri();
		    if (uri.isPlatformResource()) {
		        return new FileEditorInput(ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(uri.toPlatformString(true))));
		    } else {
		    	FileEditorInput ei = new FileEditorInput(ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(new Path(uri.toFileString())));
		    	return ei;
		    }
		}
		return null;
	}

	public IFeatureProvider getFeatureProvider() {
		if (getDiagramTypeProvider() != null) return getDiagramTypeProvider().getFeatureProvider();
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.graphiti.ui.editor.DiagramEditor#getAdapter(java.lang.Class)
	 */
	@Override
	public Object getAdapter(Class type) {
		if (type == GraphicalViewer.class || 
			type == GraphicalEditorWithFlyoutPalette.class || 
			type == EditPartViewer.class) {
			return getGraphicalViewer();
	
		/** this adapter is responsible for the outline page **/
		} else if (type == IContentOutlinePage.class) {
			return this.outlinePage;
	
		} else if (type == CommandStack.class) {
			return getCommandStack();
		} else if (type == EditDomain.class) {
			return getEditDomain();
		} else if (type == ActionRegistry.class) {
			return getActionRegistry();
		} else if (type == ZoomManager.class) {
			GraphicalViewer graphicalViewer = getGraphicalViewer();
			if (graphicalViewer == null)
				return null;
			
			RootEditPart root = graphicalViewer.getRootEditPart();
			if (root instanceof ScalableRootEditPartAnimated) {
				ScalableRootEditPartAnimated scalableRoot = (ScalableRootEditPartAnimated) root;
				return scalableRoot.getZoomManager();
			} else if (root instanceof ScalableRootEditPart) {
				return ((ScalableRootEditPart) root).getZoomManager();
			}
			return null;
		}
		
		return super.getAdapter(type);
	}
	
	/**
	 * @return the parent
	 */
	public CamelEditor getParent() {
		return this.parent;
	}
	
	/**
	 * @return the selectedContainer
	 */
	public CamelModelElement getSelectedContainer() {
		return this.selectedContainer;
	}
	
	/**
	 * sets the selected route
	 * 
	 * @param route
	 */
	public void setSelectedContainer(CamelModelElement route) {
		this.selectedContainer = route;
		switchContainer();
	}
	
	/**
	 * switches the selected container
	 */
	protected void switchContainer() {
		Runnable r = new Runnable() {
			@Override
			public void run() {
				parent.stopDirtyListener();
				CamelModelElement container = getSelectedContainer() != null ? getSelectedContainer() : getModel();
				// reimport diagram contents
				ImportCamelContextElementsCommand importCommand = new ImportCamelContextElementsCommand(CamelDesignEditor.this, getEditingDomain(), container, null);
		        getEditingDomain().getCommandStack().execute(importCommand);
		        initializeDiagram(importCommand.getDiagram());
		        refreshDiagramContents(importCommand.getDiagram());
		        update();
		        parent.updateSelectedContainer(getSelectedContainer() != null ? getSelectedContainer().getId() : getModel().getCamelContext().getId());
		        outlinePage.changeInput(container);
			}
		};
		Display.getDefault().asyncExec(r);
	}

	/**
	 * refreshes the diagram contents
	 * 
	 * @param diagram
	 */
	public void refreshDiagramContents(Diagram diagram) {
		if (getDiagramTypeProvider() != null) {
			getDiagramTypeProvider().init(diagram != null ? diagram : getDiagramTypeProvider().getDiagram(), getDiagramBehavior());
		}
        setPictogramElementsForSelection(null);
        GraphicalViewer graphicalViewer = getGraphicalViewer();
	        
        if (graphicalViewer == null)
        	return;
	        
        // set Diagram as contents for the graphical viewer and refresh
        graphicalViewer.setContents(getDiagramTypeProvider().getDiagram());	        
		getDiagramBehavior().getRefreshBehavior().initRefresh();
        getDiagramBehavior().refreshContent();
        
        refreshOutlineView();
	}
	
	/* (non-Javadoc)
	 * @see org.fusesource.ide.camel.model.service.core.model.ICamelModelListener#modelChanged()
	 */
	@Override
	public void modelChanged() {
		// we only update if the correct editor tab is selected
		parent.setDirtyFlag(true);
		if (getParent().getActivePage() != CamelEditor.DESIGN_PAGE_INDEX) return;
		Display.getDefault().syncExec(new Runnable() {
			/*
			 * (non-Javadoc)
			 * @see java.lang.Runnable#run()
			 */
			@Override
			public void run() {
				DiagramOperations.updateSelectedNode(CamelDesignEditor.this);
				getDiagramTypeProvider().getDiagramBehavior().refresh();
				if (selectedEditPart != null) {
					selectedEditPart.refresh();
				} else if (lastSelectedEditPart == null) {

				} else {
					lastSelectedEditPart.refresh();
				}
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsAdded(org.eclipse.debug.core.model.IBreakpoint[])
	 */
	@Override
	public void breakpointsAdded(IBreakpoint[] breakpoints) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				refreshDiagramContents(null);				
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsChanged(org.eclipse.debug.core.model.IBreakpoint[], org.eclipse.core.resources.IMarkerDelta[])
	 */
	@Override
	public void breakpointsChanged(IBreakpoint[] breakpoints,
			IMarkerDelta[] deltas) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				refreshDiagramContents(null);				
			}
		});
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IBreakpointsListener#breakpointsRemoved(org.eclipse.debug.core.model.IBreakpoint[], org.eclipse.core.resources.IMarkerDelta[])
	 */
	@Override
	public void breakpointsRemoved(IBreakpoint[] breakpoints,
			IMarkerDelta[] deltas) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				refreshDiagramContents(null);				
			}
		});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IDebugEventSetListener#handleDebugEvents(org.eclipse.debug.core.DebugEvent[])
	 */
	@Override
	public void handleDebugEvents(DebugEvent[] events) {
		for (DebugEvent ev : events) {
			if (ev.getSource() instanceof CamelThread == false && ev.getSource() instanceof CamelStackFrame == false) continue;
			if (ev.getSource() instanceof CamelThread && (ev.getKind() == DebugEvent.TERMINATE || ev.getKind() == DebugEvent.RESUME)) {
				// we are only interested in hit camel breakpoints
				resetHighlightBreakpointNode();
			} else {
				if (ev.getSource() instanceof CamelThread) {
					CamelThread thread = (CamelThread)ev.getSource();
					if (ev.getKind() == DebugEvent.SUSPEND && ev.getDetail() == DebugEvent.BREAKPOINT) {
						// a breakpoint was hit and thread is on suspend -> stack should be selected in tree now
						try {
							CamelStackFrame stackFrame = (CamelStackFrame)thread.getTopStackFrame();
							if (stackFrame != null) highlightBreakpointNodeWithID(stackFrame.getEndpointId());
						} catch (DebugException ex) {
							CamelEditorUIActivator.pluginLog().logError(ex);
						}
					}
				}
			}
		}
	}
	
	/**
	 * @return the highlightedNodeInDebugger
	 */
	public synchronized CamelModelElement getHighlightedNodeInDebugger() {
		return this.highlightedNodeInDebugger;
	}
	
	/**
	 * highlights the node currently in breakpoint
	 * 
	 * @param endpointNodeId	the node id
	 */
	private synchronized void highlightBreakpointNodeWithID(String endpointNodeId) {
		// get the correct node for the id
		final CamelModelElement node = getModel().findNode(endpointNodeId);
		
		if (node == null) return;
		if (this.highlightedNodeInDebugger != null && node.getId() != null && node.getId().equals(highlightedNodeInDebugger.getId())) return;

		// reset old highlight
		resetHighlightBreakpointNode();
		
		// add highlight to new node
		setDebugHighLight(node, true);
				
		// remember the new highlighted node
		this.highlightedNodeInDebugger = node;
	}
	
	/**
	 * resets the highlight from the highlighted node
	 */
	private synchronized void resetHighlightBreakpointNode() {
		setDebugHighLight(this.highlightedNodeInDebugger, false);
		this.highlightedNodeInDebugger = null;
	}
	
	/**
	 * switches the debug highlight for a given node on or off
	 * 
	 * @param bo			the node
	 * @param highlight		the highlight status
	 */
	private synchronized void setDebugHighLight(CamelModelElement bo, boolean highlight) {
		if (bo == null) return;
		
		// delegate to an operation command for async transacted diagram update
		DiagramOperations.highlightNode(this, bo, highlight);
	}

	protected RootEditPart getRootEditPart() {
		return getGraphicalViewer().getRootEditPart();
	}
	
	/* (non-Javadoc)
	 * @see org.fusesource.ide.camel.editor.utils.INodeViewer#getSelectedNode()
	 */
	@Override
	public CamelModelElement getSelectedNode() {
		return NodeUtils.getSelectedNode(Selections.getSelection(getEditorSite()));
	}
	
	/**
	 * Finds the given node by walking the edit part tree looking for the correct one
	 */
	@SuppressWarnings("unchecked")
	public static EditPart findEditPart(CamelModelElement node, EditPart part) {
		if (part instanceof RootEditPart) {
			RootEditPart root = (RootEditPart) part;
			EditPart contents = root.getContents();
			if (contents != null) {
				return findEditPart(node, contents);
			}
		}
		if (part instanceof EditPart) {
			EditPart nodeEditPart = part;
			CamelModelElement modelNode = NodeUtils.toCamelElement(nodeEditPart);
			if (Objects.equal(node, modelNode) || node.getId().equals(modelNode.getId())) {
				return nodeEditPart;
			}
		}
		List<EditPart> children = part.getChildren();
		for (EditPart childPart : children) {
			EditPart answer = findEditPart(node, childPart);
			if (answer != null) {
				return answer;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.fusesource.ide.camel.editor.utils.INodeViewer#setSelectedNode(org.fusesource.ide.camel.model.service.core.model.CamelModelElement)
	 */
	@Override
	public void setSelectedNode(CamelModelElement newSelection) {
		if (newSelection != null) {
			Object editPart = getGraphicalViewer().getEditPartRegistry().get(getFeatureProvider().getPictogramElementForBusinessObject(newSelection));
			if (editPart != null) {
				getEditorSite().getSelectionProvider().setSelection(new StructuredSelection(editPart));
			} else {
				CamelEditorUIActivator.pluginLog().logError("Could not select editPart for selection: " + newSelection);
			}
		}
	}
	
	/**
	 * layouts the camel diagram elements
	 */
	public void autoLayoutRoute() {
		Display.getCurrent().asyncExec(new Runnable() {
			/*
			 * (non-Javadoc)
			 * @see java.lang.Runnable#run()
			 */
			@Override
			public void run() {
				DiagramOperations.layoutDiagram(CamelDesignEditor.this);	
			}
		});		
	}
	
	/**
	 * changes the grid visibility
	 */
	public void setupGridVisibilityAsync() {
		// this can't be invoked async as its causing dirty editor all the time then
		DiagramOperations.updateGridColor(CamelDesignEditor.this);
				
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
			    setupGridVisibility();
	        }
	    });
	}
	
	/**
	 * refreshes the outline view
	 */
	public void refreshOutlineView() {
		if(outlinePage != null){
			this.outlinePage.modelChanged();
		}
	}
	
	/**
	 * changes the grid visibility
	 */
	private void setupGridVisibility() {
		// retrieve the grid visibility setting
		boolean gridVisible = PreferenceManager.getInstance().loadPreferenceAsBoolean(PreferencesConstants.EDITOR_GRID_VISIBILITY);

		// reset the grid visibility flag
		DiagramUtils.setGridVisible(gridVisible, this);
		
		getDiagramBehavior().refresh();
	}
	
	/**
	 * refresh the editor contents
	 */
	public void update() {
		DiagramOperations.updateDiagram(CamelDesignEditor.this);
		getDiagramBehavior().refresh();
		if(getModel() != null){
			Diagram diagram = getDiagramTypeProvider().getDiagram();
			if (diagram != null){
				selectPictogramElements(new PictogramElement[] { diagram.getContainer() });
			}
		}
	}
	
	/**
	 * clears the cache
	 */
	public void clearCache() {
		getDiagramTypeProvider().getDiagram().eResource().eAdapters().clear();
		getEditingDomain().getResourceSet().getResources().remove(getDiagramTypeProvider().getDiagram().eResource());
	}
}
