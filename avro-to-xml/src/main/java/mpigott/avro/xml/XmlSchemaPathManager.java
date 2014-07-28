/**
 * Copyright 2014 Mike Pigott
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mpigott.avro.xml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating {@link XmlSchemaPathNode}s.  This allows
 * for recyling and abstracts away the complexity of walking through an
 * XML Schema.
 *
 * @author  Mike Pigott
 */
final class XmlSchemaPathManager {

  /**
   * Constructs the document path node factory.
   */
  public XmlSchemaPathManager() {
    unusedPathNodes = new ArrayList<XmlSchemaPathNode>();
    unusedDocNodes = new ArrayList<XmlSchemaDocumentNode>();
  }

  XmlSchemaPathNode createStartPathNode(
      XmlSchemaPathNode.Direction direction,
      XmlSchemaStateMachineNode state) {

    return createPathNode(direction, null, state);
  }

  XmlSchemaPathNode createStartPathNode(
      XmlSchemaPathNode.Direction direction,
      XmlSchemaDocumentNode documentNode) {

    XmlSchemaPathNode node =
        createStartPathNode(direction, documentNode.getStateMachineNode());
    node.setDocumentNode(documentNode);

    return node;
  }

  XmlSchemaPathNode addParentSiblingOrContentNodeToPath(
      XmlSchemaPathNode startNode,
      XmlSchemaPathNode.Direction direction) {

    XmlSchemaDocumentNode position = startNode.getDocumentNode();

    switch (direction) {
    case PARENT:
      if (position != null) {
        position = position.getParent();
      }
    case SIBLING:
    case CONTENT:
      if (position == null) {
        throw new IllegalStateException("When calling addParentSiblingOrContentNodeToPath(), the startNode's document node (and its parent) cannot be null.");
      }
      break;
    default:
      throw new IllegalStateException("This method cannot be called if following a child.  Use addChildNodeToPath(startNode, direction, stateIndex).");
    }

    XmlSchemaPathNode node = null;
    if ( !unusedPathNodes.isEmpty() ) {
      node =
          unusedPathNodes.remove(unusedPathNodes.size() - 1);
      node.update(direction, startNode, position);
    } else {
      node = new XmlSchemaPathNode(direction, startNode, position);
    }

    return node;
  }

  XmlSchemaPathNode addChildNodeToPath(
      XmlSchemaPathNode startNode,
      XmlSchemaPathNode.Direction direction,
      int branchIndex) {

    if (!direction.equals(XmlSchemaPathNode.Direction.CHILD)) {
      throw new IllegalStateException("This method can only be called if following a child.  Use addParentSiblingOrContentNodeToPath(startNode, direction, position) instead.");
    }

    final XmlSchemaStateMachineNode stateMachine =
        startNode.getStateMachineNode();

    if (stateMachine.getPossibleNextStates() == null) {
      throw new IllegalStateException("Cannot follow the branch index; no possible next states.");
    } else if (stateMachine.getPossibleNextStates().size() <= branchIndex) {
      throw new IllegalArgumentException("Cannot follow the branch index; branch " + branchIndex + " was requested when there are only " + stateMachine.getPossibleNextStates().size() + " branches to follow.");
    }

    final XmlSchemaPathNode next =
        createPathNode(
            direction,
            startNode,
            stateMachine.getPossibleNextStates().get(branchIndex));

    final XmlSchemaDocumentNode docNode = startNode.getDocumentNode();
    if ((startNode.getDocumentNode() != null)
        && (docNode.getChildren() != null)) {
      next.setDocumentNode( docNode.getChildren().get(branchIndex) );
    }

    return next;
  }

  /**
   * Recyles the provided {@link XmlSchemaPathNode} and all of
   * the nodes that follow it.  Unlinks from its previous node.
   */
  void recyclePathNode(XmlSchemaPathNode toRecycle) {
    toRecycle.getPrevious().setNextNode(-1, null);
    toRecycle.setPreviousNode(null);

    if (toRecycle.getNext() != null) {
      recyclePathNode(toRecycle.getNext());
    }

    unusedPathNodes.add(toRecycle);
  }

  XmlSchemaPathNode clone(XmlSchemaPathNode original) {
    final XmlSchemaPathNode clone =
        createPathNode(
            original.getDirection(),
            original.getPrevious(),
            original.getStateMachineNode());

    clone.setIteration(original.getIteration());

    if (original.getDocumentNode() != null) {
      clone.setDocumentNode(original.getDocumentNode());
    }

    return clone;
  }

  /**
   * Follows the path starting at <code>startNode</code>, creating
   * {@link XmlSchemaDocumentNode}s and linking them along the way.
   *
   * @param startNode The node to start building the tree from.
   */
  void followPath(XmlSchemaPathNode startNode) {
    if (startNode.getDocumentNode() == null) {
      if (!startNode
             .getDirection()
             .equals(XmlSchemaPathNode.Direction.CHILD)) {

        throw new IllegalStateException("The startNode may only have a null XmlSchemaDocumentNode if it represents the root node, and likewise its only valid direction is CHILD, not " + startNode.getDirection());
      }
      // startNode is the root node.
      XmlSchemaDocumentNode rootDoc =
          createDocumentNode(null, startNode.getStateMachineNode());
      startNode.setDocumentNode(rootDoc);
      rootDoc.addVisitor(startNode);
    }

    XmlSchemaPathNode prev = startNode;
    XmlSchemaPathNode iter = prev.getNext();
    while (iter != null) {
      if (iter.getDocumentNode() == null) {
        if ( !iter.getDirection().equals(XmlSchemaPathNode.Direction.CHILD) ) {
          throw new IllegalStateException("XmlSchemaPathNode has a direction of " + iter.getDirection() + " but it does not have an XmlSchemaDocumentNode to represent its state machine (" + iter.getStateMachineNode() + ").");
        }

        final XmlSchemaDocumentNode newDocNode = 
            createDocumentNode(
                prev.getDocumentNode(),
                iter.getStateMachineNode());

        iter.setDocumentNode(newDocNode);

        final Map<Integer, XmlSchemaDocumentNode> siblings =
            prev.getDocumentNode().getChildren();

        if (prev.getIndexOfNextNodeState() < 0) {
          throw new IllegalStateException("Creating a new document node for a node represented by " + iter.getStateMachineNode() + " but its previous state does not know how to reach me.");
        }

        siblings.put(prev.getIndexOfNextNodeState(), iter.getDocumentNode());
      }

      switch (iter.getDirection()) {
      case CHILD:
      case SIBLING:
        iter.getDocumentNode().addVisitor(iter);
        break;
      default:
      }

      if (iter.getIteration() != iter.getDocIteration()) {
        throw new IllegalStateException("The current path node (representing " + iter.getStateMachineNode() + ") has an iteration of " + iter.getIteration() + ", which does not match the document node iteration of " + iter.getDocIteration() + '.');
      }

      prev = iter;
      iter = iter.getNext();
    }
  }

  void unfollowPath(XmlSchemaPathNode startNode) {
    // Walk to the end and work backwards, recycling as we go.
    XmlSchemaPathNode iter = startNode;
    XmlSchemaPathNode prev = null;

    while (iter != null) {
      prev = iter;
      iter = iter.getNext();
    }

    while (prev != startNode) {
      iter = prev;
      prev = iter.getPrevious();

      iter.getDocumentNode().removeVisitor(iter);
      if (iter.getDocIteration() == 0) {
        recycleDocumentNode(iter.getDocumentNode());
      }
      recyclePathNode(iter);
    }
  }

  private XmlSchemaPathNode createPathNode(
      XmlSchemaPathNode.Direction direction,
      XmlSchemaPathNode previous,
      XmlSchemaStateMachineNode state) {
    
    if ( !unusedPathNodes.isEmpty() ) {
      XmlSchemaPathNode node =
          unusedPathNodes.remove(unusedPathNodes.size() - 1);
      node.update(direction, previous, state);
      return node;
    } else {
      return new XmlSchemaPathNode(direction, previous, state);
    }
  }

  private XmlSchemaDocumentNode createDocumentNode(
      XmlSchemaDocumentNode parent,
      XmlSchemaStateMachineNode state) {

    if ( !unusedDocNodes.isEmpty() ) {
      XmlSchemaDocumentNode node =
          unusedDocNodes.remove(unusedDocNodes.size() - 1);
      node.set(parent, state);
      return node;
    } else {
      return new XmlSchemaDocumentNode(parent, state);
    }
  }

  // TODO: Can we include a parent's index argument?
  void recycleDocumentNode(XmlSchemaDocumentNode node) {
    if (node.getParent() != null) {
      final Map<Integer, XmlSchemaDocumentNode> siblings =
          node.getParent().getChildren();

      for (Map.Entry<Integer, XmlSchemaDocumentNode> sibling :
             siblings.entrySet()) {

        if (sibling.getValue() == node) {
          siblings.remove(sibling.getKey());
          break;
        }
      }

      if (node.getChildren() != null) {
        for (Map.Entry<Integer, XmlSchemaDocumentNode> child :
                node.getChildren().entrySet()) {
          recycleDocumentNode(child.getValue());
        }
      }
    }
  }

  private ArrayList<XmlSchemaPathNode> unusedPathNodes;
  private ArrayList<XmlSchemaDocumentNode> unusedDocNodes;
}
