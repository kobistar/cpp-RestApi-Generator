package org.openapitools.codegen.languages;

import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.openapitools.codegen.*;
import org.openapitools.codegen.meta.features.*;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.OperationMap;
import org.openapitools.codegen.model.OperationsMap;
import org.openapitools.codegen.utils.ModelUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public abstract class CppPocoAbstractCodegen extends AbstractCppCodegen implements CodegenConfig {

    private final Logger LOGGER = LoggerFactory.getLogger(CppPocoAbstractCodegen.class);
    protected final String PREFIX = "OAI";
    protected String apiVersion = "1.0.0";
    protected static final String CPP_NAMESPACE = "cppNamespace";
    protected static final String CPP_NAMESPACE_DESC = "C++ namespace (convention: name::space::for::api).";
    protected static final String CONTENT_COMPRESSION_ENABLED = "contentCompression";
    protected static final String CONTENT_COMPRESSION_ENABLED_DESC = "Enable Compressed Content Encoding for requests and responses";
    protected Set<String> foundationClasses = new HashSet<>();
    protected String cppNamespace = "OpenAPI";
    protected Map<String, String> namespaces = new HashMap<>();
    protected Set<String> systemIncludes = new HashSet<>();
    protected boolean isContentCompressionEnabled = false;

    protected Set<String> nonFrameworkPrimitives = new HashSet<>();

    public CppPocoAbstractCodegen() {
        super();

        modifyFeatureSet(features -> features
                .excludeWireFormatFeatures(WireFormatFeature.PROTOBUF)
                .securityFeatures(EnumSet.noneOf(SecurityFeature.class))
                .excludeGlobalFeatures(
                        GlobalFeature.XMLStructureDefinitions,
                        GlobalFeature.Callbacks,
                        GlobalFeature.LinkObjects,
                        GlobalFeature.ParameterStyling,
                        GlobalFeature.MultiServer
                )
                .includeSchemaSupportFeatures(
                        SchemaSupportFeature.Polymorphism
                )
                .includeParameterFeatures(
                        ParameterFeature.Cookie
                )
        );

        // set modelNamePrefix as default for QHttpEngine Server
        if (StringUtils.isEmpty(modelNamePrefix)) {
            modelNamePrefix = PREFIX;
        }
        // CLI options
        addOption(CPP_NAMESPACE, CPP_NAMESPACE_DESC, this.cppNamespace);
        addOption(CodegenConstants.MODEL_NAME_PREFIX, CodegenConstants.MODEL_NAME_PREFIX_DESC, this.modelNamePrefix);
        addSwitch(CONTENT_COMPRESSION_ENABLED, CONTENT_COMPRESSION_ENABLED_DESC, this.isContentCompressionEnabled);

        /*
         * Additional Properties.  These values can be passed to the templates and
         * are available in models, apis, and supporting files
         */
        additionalProperties.put("apiVersion", apiVersion);
        additionalProperties().put("prefix", PREFIX);

        // Write defaults namespace in properties so that it can be accessible in templates.
        // At this point command line has not been parsed so if value is given
        // in command line it will supersede this content
        additionalProperties.put("cppNamespace", cppNamespace);
        /*
         * Language Specific Primitives.  These types will not trigger imports by
         * the client generator
         */
        languageSpecificPrimitives = new HashSet<>(
                Arrays.asList(
                        "bool",
                        "Poco::Int32",/* Poco::Int32 */
                        "Poco::Int64",/* Poco::Int64 */
                        "float",
                        "double")
        );
        nonFrameworkPrimitives.addAll(languageSpecificPrimitives);

        foundationClasses.addAll(
                Arrays.asList(
                        "std::string",
                        "Date",
                        "DateTime",
                        "ByteArray")
        );
        languageSpecificPrimitives.addAll(foundationClasses);
        super.typeMapping = new HashMap<>();

        typeMapping.put("date", "Date");
        typeMapping.put("DateTime", "Poco::DateTime");   /* Poco::DateTime */
        typeMapping.put("string", "std::string");
        typeMapping.put("integer", "Poco::Int32");  /* Poco::Int32 */
        typeMapping.put("long", "Poco::Int64");     /* Poco::Int64 */
        typeMapping.put("boolean", "bool");
        typeMapping.put("number", "double");
        typeMapping.put("array", "std::list");
        typeMapping.put("map", "std::map");
        typeMapping.put("set", "std::set");
        typeMapping.put("object", PREFIX + "Object");
        // mapped as "file" type for OAS 3.0
        typeMapping.put("ByteArray", "std::vector<unsigned char>");
        //   UUID support - possible enhancement : use QUuid instead of QString.
        //   beware though that Serialization/de-serialization of QUuid does not
        //   come out of the box and will need to be sorted out (at least imply
        //   modifications on multiple templates)
        typeMapping.put("UUID", "std::string");
        typeMapping.put("URI", "Poco::URI");
        typeMapping.put("file", "Poco::File");
        typeMapping.put("binary", "std::vector<unsigned char>");
        importMapping = new HashMap<>();
        namespaces = new HashMap<>();

        systemIncludes.add("string");
        systemIncludes.add("list");
        systemIncludes.add("map");
        systemIncludes.add("set");
        systemIncludes.add("date");
        systemIncludes.add("DateTime");
        systemIncludes.add("ByteArray");
    }

    @Override
    public void processOpts() {
        super.processOpts();

        if (additionalProperties.containsKey("cppNamespace")) {
            cppNamespace = (String) additionalProperties.get("cppNamespace");
        }

        additionalProperties.put("cppNamespaceDeclarations", cppNamespace.split("\\::"));

        if (additionalProperties.containsKey("modelNamePrefix")) {
            modelNamePrefix = (String) additionalProperties.get("modelNamePrefix");
            typeMapping.put("object", modelNamePrefix + "Object");
            additionalProperties().put("prefix", modelNamePrefix);
        }
        if (additionalProperties.containsKey(CONTENT_COMPRESSION_ENABLED)) {
            setContentCompressionEnabled(convertPropertyToBooleanAndWriteBack(CONTENT_COMPRESSION_ENABLED));
        } else {
            additionalProperties.put(CONTENT_COMPRESSION_ENABLED, isContentCompressionEnabled);
        }
    }

    @Override
    public String toModelImport(String name) {
        if (name.contains("std::"))
            return "#include <" + name.split("::", 2)[1] + ">";
        if (name.isEmpty()) {
            return null;
        }

        if (name.contains("Poco::Int")){
            return "#include <Poco/Types.h>";
        } else if (name.contains("Poco::")){
            return "#include <" + name.replace("::", File.separator) + ".h>";
        }
            

        if (namespaces.containsKey(name)) {
            return "using " + namespaces.get(name) + ";";
        } else if (systemIncludes.contains(name)) {
            return "#include <" + name + ">";
        } else if (importMapping.containsKey(name)) {
            return importMapping.get(name);
        }

        String folder = modelPackage().replace("::", File.separator);
        if (!folder.isEmpty())
            folder += File.separator;

        return "#include \"" + folder + name + ".h\"";
    }

    /**
     * Optional - type declaration.  This is a String which is used by the templates to instantiate your
     * types.  There is typically special handling for different property types
     *
     * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
     */
    @Override
    @SuppressWarnings("rawtypes")
    public String getTypeDeclaration(Schema p) {
        String openAPIType = getSchemaType(p);

        if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();
            return getSchemaType(p) + "<" + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema inner = getAdditionalProperties(p);
            return getSchemaType(p) + "<std::string, " + getTypeDeclaration(inner) + ">";
        } else if (ModelUtils.isBinarySchema(p)) {
            return getSchemaType(p);
        } else if (ModelUtils.isFileSchema(p)) {
            return getSchemaType(p);
        }
        if (foundationClasses.contains(openAPIType)) {
            return openAPIType;
        } else if (languageSpecificPrimitives.contains(openAPIType)) {
            return toModelName(openAPIType);
        } else {
            return openAPIType;
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public String toDefaultValue(Schema p) {
        if (ModelUtils.isBooleanSchema(p)) {
            return "false";
        } else if (ModelUtils.isDateSchema(p)) {
            return "NULL";
        } else if (ModelUtils.isDateTimeSchema(p)) {
            return "NULL";
        } else if (ModelUtils.isNumberSchema(p)) {
            if (SchemaTypeUtil.FLOAT_FORMAT.equals(p.getFormat())) {
                return "0.0f";
            }
            return "0.0";
        } else if (ModelUtils.isIntegerSchema(p)) {
            if (SchemaTypeUtil.INTEGER64_FORMAT.equals(p.getFormat())) {
                return "0L";
            }
            return "0";
        } else if (ModelUtils.isMapSchema(p)) {
            Schema inner = getAdditionalProperties(p);
            return "map<std::string, " + getTypeDeclaration(inner) + ">()";
        } else if (ModelUtils.isArraySchema(p)) {
            ArraySchema ap = (ArraySchema) p;
            Schema inner = ap.getItems();
            return "list<" + getTypeDeclaration(inner) + ">()";
        } else if (ModelUtils.isStringSchema(p)) {
            return "std::string(\"\")";
        } else if (!StringUtils.isEmpty(p.get$ref())) {
            return toModelName(ModelUtils.getSimpleRef(p.get$ref())) + "()";
        }
        return "NULL";
    }

    @Override
    public String toModelFilename(String name) {
        return toModelName(name);
    }

    /**
     * Optional - OpenAPI type conversion.  This is used to map OpenAPI types in a `Schema` into
     * either language specific types via `typeMapping` or into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     */
    @Override
    @SuppressWarnings("rawtypes")
    public String getSchemaType(Schema p) {
        String openAPIType = super.getSchemaType(p);

        String type = null;
        if (typeMapping.containsKey(openAPIType)) {
            type = typeMapping.get(openAPIType);
            if (languageSpecificPrimitives.contains(type)) {
                return toModelName(type);
            }
            if (foundationClasses.contains(type)) {
                return type;
            }
        } else {
            type = openAPIType;
        }
        return toModelName(type);
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        String varName = name;
        varName = sanitizeName(name);

        // if it's all upper case, convert to lower case
        if (varName.matches("^[A-Z_]*$")) {
            varName = varName.toLowerCase(Locale.ROOT);
        }

        // camelize (lower first character) the variable name
        // petId => pet_id
        varName = org.openapitools.codegen.utils.StringUtils.underscore(varName);

        // for reserved word or word starting with number, append _
        if (isReservedWord(varName) || varName.matches("^\\d.*")) {
            varName = escapeReservedWord(varName);
        }

        return varName;
    }

    @Override
    public String toParamName(String name) {
        return toVarName(name);
    }

    @Override
    public String getTypeDeclaration(String str) {
        return str;
    }

    @Override
    protected boolean needToImport(String type) {
        return StringUtils.isNotBlank(type) && !defaultIncludes.contains(type)
                && !nonFrameworkPrimitives.contains(type);
    }


    @Override
    public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
        OperationMap objectMap = objs.getOperations();
        List<CodegenOperation> operations = objectMap.getOperation();

        List<Map<String, String>> imports = objs.getImports();
        Map<String, CodegenModel> codegenModels = new HashMap<>();

        for (ModelMap moObj : allModels) {
            CodegenModel mo = moObj.getModel();
            if (mo.isEnum) {
                codegenModels.put(mo.classname, mo);
            }
        }
        for (CodegenOperation operation : operations) {
            if (operation.returnType != null) {
                if (codegenModels.containsKey(operation.returnType)) {
                    operation.vendorExtensions.put("x-returns-enum", true);
                }
            }
            // Check all return parameter baseType if there is a necessity to include, include it if not
            // already done
            if (operation.returnBaseType != null && needToImport(operation.returnBaseType)) {
                if (!isIncluded(operation.returnBaseType, imports)) {
                    imports.add(createMapping("import", operation.returnBaseType));
                }
            }
            List<CodegenParameter> params = new ArrayList<>();
            if (operation.allParams != null) params.addAll(operation.allParams);

            // Check all parameter baseType if there is a necessity to include, include it if not
            // already done
            for (CodegenParameter param : params) {
                if (param.isPrimitiveType && needToImport(param.baseType)) {
                    if (!isIncluded(param.baseType, imports)) {
                        imports.add(createMapping("import", param.baseType));
                    }
                }
            }
            if (operation.pathParams != null) {
                // We use std::string to pass path params, add it to include
                if (!isIncluded("std::string", imports)) {
                    imports.add(createMapping("import", "std::string"));
                }
            }
        }
        if (isIncluded("map", imports)) {
            // Maps uses std::string as key
            if (!isIncluded("std::string", imports)) {
                imports.add(createMapping("import", "std::string"));
            }
        }
        return objs;
    }

    @Override
    public String toEnumValue(String value, String datatype) {
        return escapeText(value);
    }

    @Override
    public boolean isDataTypeString(String dataType) {
        return "std::string".equals(dataType);
    }

    private Map<String, String> createMapping(String key, String value) {
        Map<String, String> customImport = new HashMap<>();
        customImport.put(key, toModelImport(value));
        return customImport;
    }

    private boolean isIncluded(String type, List<Map<String, String>> imports) {
        boolean included = false;
        String inclStr = toModelImport(type);
        for (Map<String, String> importItem : imports) {
            if (importItem.containsValue(inclStr)) {
                included = true;
                break;
            }
        }
        return included;
    }

    public void setContentCompressionEnabled(boolean flag) {
        this.isContentCompressionEnabled = flag;
    }
}
