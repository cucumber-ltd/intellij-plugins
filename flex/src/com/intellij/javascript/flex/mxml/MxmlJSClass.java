package com.intellij.javascript.flex.mxml;

import com.intellij.javascript.flex.FlexPredefinedTagNames;
import com.intellij.lang.javascript.JSLanguageDialect;
import com.intellij.lang.javascript.JavaScriptSupportLoader;
import com.intellij.lang.javascript.flex.XmlBackedJSClassImpl;
import com.intellij.lang.javascript.psi.JSFile;
import com.intellij.lang.javascript.psi.JSType;
import com.intellij.lang.javascript.psi.JSVariable;
import com.intellij.lang.javascript.psi.ecmal4.*;
import com.intellij.lang.javascript.psi.resolve.ImplicitJSVariableImpl;
import com.intellij.lang.javascript.psi.resolve.JSResolveUtil;
import com.intellij.lang.javascript.psi.resolve.ResolveProcessor;
import com.intellij.lang.javascript.psi.types.JSContext;
import com.intellij.lang.javascript.psi.types.JSNamedType;
import com.intellij.lang.javascript.psi.types.JSTypeSourceFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

import static com.intellij.lang.javascript.psi.JSCommonTypeNames.OBJECT_CLASS_NAME;

/**
 * @author yole
 */
public class MxmlJSClass extends XmlBackedJSClassImpl {
  @NonNls public static final String XML_TAG_NAME = "XML";
  @NonNls public static final String XMLLIST_TAG_NAME = "XMLList";
  @NonNls public static final String PRIVATE_TAG_NAME = MxmlLanguageInjector.PRIVATE_TAG_NAME;
  public static final @NonNls String MXML_URI4 = "library://ns.adobe.com/flex/spark";
  public static final @NonNls String MXML_URI5 = "library://ns.adobe.com/flex/halo";
  public static final @NonNls String MXML_URI6 = "library://ns.adobe.com/flex/mx";
  public static final @NonNls String[] MXML_URIS = {JavaScriptSupportLoader.MXML_URI, JavaScriptSupportLoader.MXML_URI3, MXML_URI4, MXML_URI5, MXML_URI6};
  public static final @NonNls String[] FLEX_4_NAMESPACES = {JavaScriptSupportLoader.MXML_URI3, MXML_URI4, MXML_URI5, MXML_URI6};
  private static final String OPERATION_TAG_NAME = "operation";
  private static final String HTTP_SERVICE_TAG_NAME = "HTTPService";
  private static final String WEB_SERVICE_TAG_NAME = "WebService";
  private static final String[] REQUEST_TAG_POSSIBLE_NAMESPACES =
    {JavaScriptSupportLoader.MXML_URI, MXML_URI4, MXML_URI6};
  private static final String REQUEST_TAG_NAME = "request";
  private static final String[] TAGS_THAT_ALLOW_ANY_XML_CONTENT = {PRIVATE_TAG_NAME, XML_TAG_NAME, XMLLIST_TAG_NAME,
    FlexPredefinedTagNames.MODEL};
  @NonNls private static final String FXG_SUPER_CLASS = "spark.core.SpriteVisualElement";

  private static final Logger LOG = Logger.getInstance(MxmlJSClass.class);

  private static Key<CachedValue<List<JSVariable>>> ourSkinComponentPredefinedVarsKey = Key.create("ourskinComponentPredefinedVarsKey");
  private static JSResolveUtil.ImplicitVariableProvider skinComponentPredefinedVars = new JSResolveUtil.ImplicitVariableProvider() {
    protected void doComputeVars(final List<JSVariable> vars, final XmlFile xmlFile) {
      for (XmlTag t : xmlFile.getDocument().getRootTag().findSubTags(FlexPredefinedTagNames.METADATA, JavaScriptSupportLoader.MXML_URI3)) {
        JSResolveUtil.processInjectedFileForTag(t, new JSResolveUtil.JSInjectedFilesVisitor() {
          @Override
          protected void process(JSFile file) {
            for (PsiElement elt : file.getChildren()) {
              if (elt instanceof JSAttributeList) {
                JSAttribute[] hostAnnotation = ((JSAttributeList)elt).getAttributesByName("HostComponent");

                if (hostAnnotation.length == 1 && vars.size() == 0) {
                  JSAttributeNameValuePair valuePair = hostAnnotation[0].getValueByName(null);

                  vars.add(
                    new ImplicitJSVariableImpl(
                      "hostComponent",
                      valuePair != null ? valuePair.getSimpleValue() : OBJECT_CLASS_NAME,
                      JSAttributeList.AccessType.PUBLIC,
                      xmlFile
                    )
                  );
                  return;
                }
              }
            }
          }
        });
      }
    }
  };

  private static JSResolveUtil.ImplicitVariableProvider inlineComponentRenderPredefinedVars = new JSResolveUtil.ImplicitVariableProvider() {
    protected void doComputeVars(List<JSVariable> vars, XmlFile xmlFile) {
      JSClass cls = XmlBackedJSClassFactory.getXmlBackedClass(xmlFile);
      final JSType type = JSNamedType.createType(cls.getQualifiedName(), JSTypeSourceFactory.createTypeSource(cls, true), JSContext.INSTANCE);
      vars.add(new ImplicitJSVariableImpl("outerDocument", type, xmlFile));
    }
  };

  private static Key<CachedValue<List<JSVariable>>> ourInlineComponentRenderPredefinedVarsKey = Key.create("ourInlineComponentRenderPredefinedVarsKey");

  private volatile JSReferenceList myImplementsList;
  private final boolean isFxgBackedClass;

  public MxmlJSClass(XmlTag tag) {
    super(tag);
    final PsiFile psiFile = tag.getContainingFile();
    isFxgBackedClass = psiFile != null && isFxgFile(psiFile);
  }

  public static XmlTag[] findLanguageSubTags(final XmlTag tag, final String languageTagName) {
    return tag.findSubTags(languageTagName, getLanguageNamespace(tag));
  }

  public static boolean isFxgFile(final PsiFile file) {
    return JavaScriptSupportLoader.isFxgFile(file.getViewProvider().getVirtualFile());
  }

  @NotNull
  public static String getLanguageNamespace(final XmlTag rootTag) {
    assert JavaScriptSupportLoader.isFlexMxmFile(rootTag.getContainingFile()) : rootTag.getContainingFile();
    return rootTag.getPrefixByNamespace(JavaScriptSupportLoader.MXML_URI3) != null
           ? JavaScriptSupportLoader.MXML_URI3
           : JavaScriptSupportLoader.MXML_URI;
  }

  @Override
  protected String getSuperClassName() {
    if (isFxgBackedClass) {
      return FXG_SUPER_CLASS;
    }
    return super.getSuperClassName();
  }

  @Override
  public String getName() {
    XmlTag parent = getParent();
    if (parent.getParentTag() != null && isComponentTag(parent.getParentTag())) {
      String explicitName = getExplicitName();
      if (explicitName != null) {
        return explicitName;
      }
    }
    return super.getName();
  }

  @Nullable
  private String getExplicitName() {
    XmlTag parent = getParent();
    if (parent.getParentTag() != null) {
      return parent.getParentTag().getAttributeValue(CLASS_NAME_ATTRIBUTE_NAME, parent.getParentTag().getNamespace());
    }
    else {
      return null;
    }
  }

  @Nullable
  @Override
  public JSReferenceList getImplementsList() {
    if (isFxgBackedClass) {
      return null;
    }
    JSReferenceList refList = myImplementsList;

    if (refList == null) {
      final XmlTag rootTag = getParent();
      myImplementsList = refList = createReferenceList(rootTag != null ? rootTag.getAttributeValue(IMPLEMENTS_ATTRIBUTE) : null);
    }
    return refList;
  }

  @Override
  public void addToImplementsList(String refText) {
    XmlAttribute attribute = getParent().getAttribute(IMPLEMENTS_ATTRIBUTE);
    if (attribute == null) {
      getParent().setAttribute(IMPLEMENTS_ATTRIBUTE, refText);
    }
    else {
      attribute.setValue(attribute.getValue() + ", " + refText);
    }
    myImplementsList = null;
  }

  @Override
  public void removeFromImplementsList(String refText) {
    String[] refs = getImplementsList().getReferenceTexts();
    LOG.assertTrue(ArrayUtil.contains(refText, refs));
    XmlAttribute attribute = getParent().getAttribute(IMPLEMENTS_ATTRIBUTE);
    if (refs.length == 1) {
      attribute.delete();
    }
    else {
      String[] newRefs = ArrayUtil.remove(refs, refText);
      attribute.setValue(StringUtil.join(newRefs, ", "));
    }
    myImplementsList = null;
  }

  public void setBaseComponent(String qName, String prefix, String namespace) {
    setBaseComponent(getParent(), qName, prefix, namespace);
    myExtendsList = null;
  }

  public static void setBaseComponent(XmlTag rootTag, String qName, String prefix, String namespace) {
    Map<String, String> existingPrefix2Namespace = rootTag.getLocalNamespaceDeclarations();
    for (Map.Entry<String, String> entry : existingPrefix2Namespace.entrySet()) {
      if (entry.getValue().equals(namespace)) {
        rootTag.setName(entry.getKey() + ":" + StringUtil.getShortName(qName));
        return;
      }
    }

    int postfix = 1;
    String uniquePrefix = prefix;
    while (existingPrefix2Namespace.containsKey(uniquePrefix)) {
      uniquePrefix = prefix + postfix++;
    }
    rootTag.setName(uniquePrefix + ":" + StringUtil.getShortName(qName));
    rootTag.setAttribute("xmlns:" + uniquePrefix, namespace);
  }

  @Override
  protected void processImplicitMembers(PsiScopeProcessor processor) {
    JSResolveUtil.processImplicitVars(processor, getContainingFile(), skinComponentPredefinedVars,
                                      ourSkinComponentPredefinedVarsKey);
  }

  @Override
  public boolean processOuterDeclarations(PsiScopeProcessor processor) {
    return JSResolveUtil.processImplicitVars(processor, getContainingFile(), inlineComponentRenderPredefinedVars,
                                             ourInlineComponentRenderPredefinedVarsKey);
  }

  public static boolean isFxLibraryTag(final XmlTag tag) {
    return tag != null && FlexPredefinedTagNames.LIBRARY.equals(tag.getLocalName()) && JavaScriptSupportLoader.MXML_URI3.equals(tag.getNamespace());
  }

  @Override
  protected boolean canResolveTo(XmlElement element) {
    final XmlTag tag = PsiTreeUtil.getParentOfType(element, XmlTag.class, false);
    if (tag == null) {
      return true;
    }
    // Component tag itself can be referenced by id
    if (!isComponentTag(tag) && getContainingComponent(element) != this) {
      return false;
    }
    return canBeReferencedById(tag);
  }

  @Override
  protected boolean resolveViaImplicitImports(ResolveProcessor processor) {
    return MxmlImplicitImports.resolveTypeNameUsingImplicitImports(processor, this);
  }

  public static boolean canBeReferencedById(XmlTag tag) {
    return !isInsideTagThatAllowsAnyXmlContent(tag) && !isOperationTag(tag);
  }

  public static boolean isTagOrInsideTagThatAllowsAnyXmlContent(final XmlTag tag) {
    return isTagThatAllowsAnyXmlContent(tag) || isInsideTagThatAllowsAnyXmlContent(tag);
  }

  public static boolean isInsideTagThatAllowsAnyXmlContent(final XmlTag tag) {
    return isInsideTag(tag, new Condition<XmlTag>() {
      public boolean value(final XmlTag parentTag) {
        return isTagThatAllowsAnyXmlContent(parentTag);
      }
    });
  }

  public static boolean isTagThatAllowsAnyXmlContent(final XmlTag tag) {
    return tag != null &&
           (JavaScriptSupportLoader.isLanguageNamespace(tag.getNamespace()) &&
            ArrayUtil.contains(tag.getLocalName(), TAGS_THAT_ALLOW_ANY_XML_CONTENT) || isWebOrHttpServiceRequestTag(tag));
  }

  private static boolean isOperationTag(final XmlTag tag) {
    return OPERATION_TAG_NAME.equals(tag.getLocalName()) && ArrayUtil.contains(tag.getNamespace(), REQUEST_TAG_POSSIBLE_NAMESPACES);
  }

  private static boolean isWebOrHttpServiceRequestTag(final XmlTag tag) {
    if (ArrayUtil.contains(tag.getNamespace(), REQUEST_TAG_POSSIBLE_NAMESPACES) && REQUEST_TAG_NAME.equals(tag.getLocalName())) {
      final XmlTag parentTag = tag.getParentTag();
      if (parentTag != null && ArrayUtil.contains(parentTag.getNamespace(), REQUEST_TAG_POSSIBLE_NAMESPACES)) {
        if (HTTP_SERVICE_TAG_NAME.equals(parentTag.getLocalName())) {
          return true;
        }
        else if (OPERATION_TAG_NAME.equals(parentTag.getLocalName())) {
          final XmlTag parentParentTag = parentTag.getParentTag();
          if (parentParentTag != null &&
              ArrayUtil.contains(parentParentTag.getNamespace(), REQUEST_TAG_POSSIBLE_NAMESPACES) &&
              WEB_SERVICE_TAG_NAME.equals(parentParentTag.getLocalName())) {
            return true;
          }
        }
      }
    }
    return false;
  }


  public JSFile createScriptTag() throws IncorrectOperationException {
    final XmlTag rootTag = getParent();

    if (rootTag != null) {
      String ns = getLanguageNamespace(rootTag);

      final String emptyText = "\n";
      for (XmlTag tag : rootTag.getSubTags()) {
        if (FlexPredefinedTagNames.SCRIPT.equals(tag.getLocalName()) && ns.equals(tag.getNamespace())) {
          tag.getValue().setText(emptyText);
          return findFirstScriptTag();
        }
      }

      rootTag.add(rootTag.createChildTag(FlexPredefinedTagNames.SCRIPT, ns, CDATA_START + emptyText + CDATA_END, false));
      return findFirstScriptTag();
    }
    return null;
  }

  @Override
  protected JSLanguageDialect getClassLanguage() {
    return JavaScriptSupportLoader.ECMA_SCRIPT_L4;
  }
}
