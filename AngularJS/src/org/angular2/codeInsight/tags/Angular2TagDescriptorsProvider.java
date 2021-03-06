// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.codeInsight.tags;

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.completion.XmlTagInsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.Language;
import com.intellij.lang.javascript.psi.stubs.impl.JSImplicitElementImpl;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.html.HtmlTag;
import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlTagNameProvider;
import com.intellij.xml.util.XmlUtil;
import icons.AngularJSIcons;
import org.angular2.codeInsight.Angular2DeclarationsScope;
import org.angular2.codeInsight.Angular2DeclarationsScope.DeclarationProximity;
import org.angular2.codeInsight.attributes.Angular2ApplicableDirectivesProvider;
import org.angular2.entities.Angular2DirectiveSelectorPsiElement;
import org.angular2.entities.Angular2EntitiesProvider;
import org.angular2.lang.Angular2LangUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class Angular2TagDescriptorsProvider implements XmlElementDescriptorProvider, XmlTagNameProvider {
  public static final String NG_CONTAINER = "ng-container";
  public static final String NG_CONTENT = "ng-content";
  public static final String NG_TEMPLATE = "ng-template";

  @Override
  public void addTagNameVariants(@NotNull final List<LookupElement> elements, @NotNull XmlTag xmlTag, String prefix) {
    if (!(xmlTag instanceof HtmlTag && Angular2LangUtil.isAngular2Context(xmlTag))) {
      return;
    }
    final Project project = xmlTag.getProject();
    Language language = xmlTag.getContainingFile().getLanguage();
    Set<String> names = new HashSet<>();
    for (LookupElement el : elements) {
      names.add(el.getLookupString());
    }
    for (String name : asList(NG_CONTAINER, NG_CONTENT, NG_TEMPLATE)) {
      if (names.add(name)) {
        addLookupItem(language, elements, name);
      }
    }
    Angular2DeclarationsScope scope = new Angular2DeclarationsScope(xmlTag);
    Angular2EntitiesProvider.getAllElementDirectives(project).forEach((name, list) -> {
      if (!list.isEmpty() && !name.isEmpty() && names.add(name)) {
        Angular2DirectiveSelectorPsiElement el = list.get(0).getSelector().getPsiElementForElement(name);
        addLookupItem(language, elements, el, name, scope.getDeclarationsProximity(list));
      }
    });
  }

  private static void addLookupItem(@NotNull Language language, @NotNull List<LookupElement> elements, @NotNull String name) {
    addLookupItem(language, elements, name, name, DeclarationProximity.IN_SCOPE);
  }

  private static void addLookupItem(@NotNull Language language,
                                    @NotNull List<LookupElement> elements,
                                    @NotNull Object component,
                                    @NotNull String name,
                                    @NotNull DeclarationProximity proximity) {
    if (proximity == DeclarationProximity.DOES_NOT_EXIST) {
      return;
    }
    LookupElementBuilder element = LookupElementBuilder.create(component, name)
      .withIcon(AngularJSIcons.Angular2);
    if (proximity == DeclarationProximity.PUBLIC_MODULE_EXPORT) {
      element = element.withItemTextForeground(SimpleTextAttributes.GRAYED_ATTRIBUTES.getFgColor());
    }
    if (language.isKindOf(XMLLanguage.INSTANCE)) {
      element = element.withInsertHandler(XmlTagInsertHandler.INSTANCE);
    }
    elements.add(PrioritizedLookupElement.withPriority(
      element, proximity == DeclarationProximity.IN_SCOPE ? 1 : 0));
  }

  @Nullable
  @Override
  public XmlElementDescriptor getDescriptor(@NotNull XmlTag xmlTag) {
    if (!(xmlTag instanceof HtmlTag && Angular2LangUtil.isAngular2Context(xmlTag))) {
      return null;
    }
    String tagName = xmlTag.getName();
    if (XmlUtil.isTagDefinedByNamespace(xmlTag)) return null;
    tagName = XmlUtil.findLocalNameByQualifiedName(tagName);
    if (NG_CONTAINER.equalsIgnoreCase(tagName) || NG_CONTENT.equalsIgnoreCase(tagName) || NG_TEMPLATE.equalsIgnoreCase(tagName)) {
      return new Angular2TagDescriptor(tagName, createDirective(xmlTag, tagName));
    }

    Angular2ApplicableDirectivesProvider provider = new Angular2ApplicableDirectivesProvider(xmlTag, true);
    if (provider.getCandidates().isEmpty()) {
      return null;
    }
    return new Angular2TagDescriptor(tagName, (provider.getMatched().isEmpty()
                                               ? provider.getCandidates()
                                               : provider.getMatched())
      .get(0)
      .getSelector()
      .getPsiElementForElement(tagName));
  }

  @NotNull
  private static JSImplicitElementImpl createDirective(@NotNull XmlTag xmlTag, @NotNull String name) {
    return new JSImplicitElementImpl.Builder(name, xmlTag).setTypeString("E;;;").toImplicitElement();
  }
}
