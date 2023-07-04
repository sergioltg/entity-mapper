package entity.mapper;

import entity.mapper.fieldmaps.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implements a simple recursive descent parser for Entity Map specifications.
 * The supported syntax directly maps the EntityMapper API.
 * <p>
 * The following is an EBNF representation of the Entity Map grammar:
 * See <a https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form></a>
 * <p>
 * Note that the extended notation set( option | option ... ) is used to specify an unordered set of options, which cannot be cleanly specified in EBNF.
 * This is distinct from a repetition of an alternation { option | option ... } which allows multiple occurrences of any option.
 * map specification        = { alias definition }, entity map;
 * <p>
 * alias definition         = "alias", identifier, "as", alias;
 * alias                    = identifier;
 * <p>
 * entity map               = specified entity map | reflected entity map;
 * specified entity map     = entity spec, field map list;
 * reflected entity map     = "<", entity class, ">";
 * <p>
 * field map list           = "{", field map, { ",", field map }, "}";
 * field map                = reflected field map | simple field map | complex field map | merged field map;
 * reflected field map      = "<>";
 * simple field map         = field spec, [ "=", literal ];
 * complex field map        = field spec, ":", ( collection map | component map );
 * merged field map         = merged field spec, ":", merged collection map;
 * field spec               = set( "exclusive" | field access ), ( internal field | key field | entity name field | attribute field ), [ "as", external field ];
 * merged field spec        = [ field access ], "merge", [ "as", external field ];
 * field access             = "readonly" | "final" | "createonly" | "writeonly" | "sensitive";
 * key field                = "(", internal field, ")";
 * entity name field        = "[", internal field, "]";
 * attribute field          = "@", plugin name, ":", internal field;
 * internal field           = identifier;
 * external field           = identifier;
 * plugin name              = identifier;
 * <p>
 * join spec                = "join", foreign key, "->", parent key, [ "using", persister class ];
 * persister class          = identifier;
 * foreign key              = identifier;
 * parent key               = identifier;
 * <p>
 * cascade spec             = "with", ( "cascade-update" | "cascade-delete" | "cascade-all" )
 * <p>
 * collection map           = specified collection map | reflected collection map;
 * specified collection map = "[", [ collection mode ], entity spec, [ join spec, [ cascade spec ] ], "]", field map list;
 * reflected collection map = "[", [ collection mode ], reflected entity map, [ join spec, [ cascade spec ] ], "]";
 * collection mode          = "lazy" | "eager";
 * <p>
 * merged collection map    = "[", [ collection mode ], variant class list, [ "indexed", "by", external field ], [ "using", handler class ], "]", field map list;
 * <p>
 * component map            = ( set( "optional" | "flattened" ), ( specified component map | reflected component map )) | subclass component map;
 * specified component map  = entity spec, [ join spec ], [ cascade spec ], field map list;
 * reflected component map  = reflected entity map, [ join spec ], [ cascade spec ];
 * subclass component map   = "subclass", entity class, [ custom map handler spec ], field map list;
 * <p>
 * entity spec              = entity class, [ "unlocalised" ], [ "(", variant class list, ")" ], [ custom map handler spec ];
 * custom map handler spec  = "using", handler class;
 * variant class list       = entity class, { "|", entity class }, [ "as", discriminator name ];
 * entity class             = identifier;
 * handler class            = identifier;
 * discriminator name       = identifier;
 * <p>
 * identifier               = ( letter | "_" | "@" ), { letter | digit | "_" | "." | "-" };
 * <p>
 * literal                  = string literal | numeric literal | boolean literal;
 * string literal           = ( '"', { character }, '"') | ( "'", { character }, "'" );
 * integer literal          = digit, { digit };
 * decimal literal          = { digit }, ".", digit, { digit } ];
 * boolean literal          = "true" | "false";
 * <p>
 * character                = ? any printable character ?;
 * digit                    = "0..9";
 * letter                   = "A".."Z" | "a..z";
 *
 */
public class EntityMapParser<T> {
    private static final Logger LOGGER = Logger.getLogger(EntityMapParser.class.getName());

    private String input;
    private String source;
    private Token _nextToken = null;
    private Map<String, String> aliasMap = new HashMap<>();

    /**
     * Returns an EntityMapParser for parsing the specified Entity Resource Map specification.
     *
     * @param input       the ERM specification
     * @param ermFileName the file from which the ERM specification was loaded (may be null)
     * @return The parsed EntityResourceMap
     */
    public EntityMapParser(String input, String ermFileName) {
        this.input = input;
        if (ermFileName == null) {
            source = "";
        } else {
            source = "in " + ermFileName;
        }
    }

    /**
     * Parses a top-level entity map specification.
     *
     * @return An EntityMapper for the parsed entity resource map.
     */
    public EntityMapper<T> parse() {
        while (peekToken().is(TokenType.Alias)) {
            nextToken();
            String fullName = nextToken().expect(TokenType.Identifier).getValue();
            nextToken().expect(TokenType.As);
            String alias = nextToken().expect(TokenType.Identifier).getValue();
            nextToken().expect(TokenType.Semicolon);
            aliasMap.put(alias, fullName);
        }
        EntityMapper entityMapper;
        switch (peekToken().expect(TokenType.OpenAngleBracket, TokenType.Identifier).getTokenType()) {
            case OpenAngleBracket: // reflected entity map
                nextToken();
                Token entityToken = nextToken().expect(TokenType.Identifier);
                Class entityClass = getEntityClass(entityToken.getValue(), null);
                EntityMapper.MapperBuilder mapperBuilder = EntityMapper.entity(new EntityMapper.EntityClassSpec<>(entityClass, entityToken.getUnresolvedValue())).map(ReflectionMap.reflection(entityClass));
                nextToken().expect(TokenType.CloseAngleBracket);
                entityMapper = mapperBuilder.build();
                break;
            default: // specified entity map
                entityMapper = parseFieldMapList(parseEntitySpec(true, null));
        }
        nextToken().expect(TokenType.EndOfInput);
        //noinspection unchecked
        return entityMapper;
    }

    /**
     * Parses a single collection map as part of a field map
     *
     * @param internalFieldName The internal field name from the field map
     * @param externalFieldName The external field name from the field map
     * @param pluginName        The plugin name associated with an attribute field
     * @param access            The access from the field map
     * @return A FieldMapProvider for the parsed map.
     */
    private FieldMapProvider parseCollectionMap(String internalFieldName, String externalFieldName, String pluginName, boolean isAttribute, FieldMap.Access access, boolean exclusive) {
        nextToken().expect(TokenType.OpenSquareBracket);

        CollectionMap.Mode collectionMode = CollectionMap.Mode.LAZY;
        if (peekToken().is(TokenType.Lazy)) {
            collectionMode = CollectionMap.Mode.LAZY;
            nextToken();
        } else if (peekToken().is(TokenType.Eager)) {
            collectionMode = CollectionMap.Mode.EAGER;
            nextToken();
        }

        JoinSpec joinSpec = new JoinSpec();
        EntityFieldMap.Cascade cascade = null;
        switch (peekToken().expect(TokenType.OpenAngleBracket, TokenType.Identifier).getTokenType()) {
            case OpenAngleBracket: // reflected collection map
                nextToken();
                Class entityClass = getEntityClass(nextToken().expect(TokenType.Identifier).getValue(), pluginName);
                nextToken().expect(TokenType.CloseAngleBracket);

                if (peekToken().is(TokenType.Join)) {
                    joinSpec = parseJoinSpec(entityClass);
                    if (peekToken().is(TokenType.With)) {
                        cascade = parseCascadeSpec();
                    }
                }

                nextToken().expect(TokenType.CloseSquareBracket);
                return CollectionMap.collection(internalFieldName, externalFieldName, pluginName, isAttribute, collectionMode, access, exclusive,
                        EntityMapper.entity(entityClass).map(ReflectionMap.reflection(entityClass)).build(), null, joinSpec.foreignKey, joinSpec.parentKey, cascade);
            default: // specified collection map
                EntitySpec entitySpec = parseEntitySpec(true, pluginName);

                if (peekToken().is(TokenType.Join)) {
                    joinSpec = parseJoinSpec(entitySpec.baseClassSpec.getEntityClass());
                    if (peekToken().is(TokenType.With)) {
                        cascade = parseCascadeSpec();
                    }
                }

                nextToken().expect(TokenType.CloseSquareBracket);

                EntityMapper entityMapper = null;
                Class primitiveType = null;

                if (entitySpec.baseClassSpec.getEntityClass() != null && isPrimitiveType(entitySpec.baseClassSpec.getEntityClass())) {
                    primitiveType = entitySpec.baseClassSpec.getEntityClass();
                } else {
                    entityMapper = parseFieldMapList(entitySpec);
                    if (entityMapper == null) {
                        return null;
                    }
                }
                return CollectionMap.collection(internalFieldName, externalFieldName, pluginName, isAttribute, collectionMode, access, exclusive,
                        entityMapper, primitiveType, joinSpec.foreignKey, joinSpec.parentKey, cascade);
        }
    }

    /**
     * Parses a single merged collection map as part of a field map
     *
     * @param externalFieldName The external field name from the field map
     * @param access            The access from the field map
     * @return A FieldMapProvider for the parsed map.
     */
    private FieldMapProvider parseMergedCollectionMap(String externalFieldName, FieldMap.Access access, boolean exclusive) {
        nextToken().expect(TokenType.OpenSquareBracket);

        CollectionMap.Mode collectionMode = CollectionMap.Mode.LAZY;
        if (peekToken().is(TokenType.Lazy)) {
            collectionMode = CollectionMap.Mode.LAZY;
            nextToken();
        } else if (peekToken().is(TokenType.Eager)) {
            collectionMode = CollectionMap.Mode.EAGER;
            nextToken();
        }

        EntitySpec<?> entitySpec = new EntitySpec();
        entitySpec.baseClassSpec = EntityMapper.EntityClassSpec.MERGE;
        for (; true; nextToken()) {
            Token entityClassToken = nextToken().expect(TokenType.Identifier);
            entitySpec.subClassSpecs.add(new EntityMapper.EntityClassSpec(getEntityClass(entityClassToken.getValue(), null), entityClassToken.getUnresolvedValue()));
            if (!peekToken().is(TokenType.OrBar)) {
                break;
            }
        }
        if (peekToken().is(TokenType.As)) {
            nextToken();
            entitySpec.externalDiscriminatorName = nextToken().expect(TokenType.Identifier).getValue();
        }

        String indexedByFieldName = null;
        if (peekToken().is(TokenType.Indexed)) {
            nextToken();
            nextToken().expect(TokenType.By);
            indexedByFieldName = nextToken().expect(TokenType.Identifier).getValue();
        }

        if (peekToken().is(TokenType.Using)) {
            nextToken();
        }

        nextToken().expect(TokenType.CloseSquareBracket);

        EntityMapper entityMapper = parseFieldMapList(entitySpec);
        if (entityMapper == null) {
            return null;
        }
        return MergedCollectionMap.mergedCollection(externalFieldName, collectionMode, access, exclusive, indexedByFieldName, entityMapper);
    }

    /**
     * Parses a single component map as part of a field map
     *
     * @param internalFieldName The internal field name from the field map
     * @param externalFieldName The external field name from the field map
     * @param pluginName        the name of a plugin upon which the entity is dependent
     * @param access            The access from the field map
     * @return A FieldMapProvider for the parsed map.
     */
    private FieldMapProvider parseComponentMap(String internalFieldName, String externalFieldName, String pluginName, boolean isAttribute, FieldMap.Access access, boolean exclusive) {
        Class entityClass;
        EntityMapper entityMapper;

        // subclass component map
        if (peekToken().is(TokenType.Subclass)) {
            nextToken();
            EntitySpec entitySpec = parseEntitySpec(false, pluginName);

            entityMapper = parseFieldMapList(entitySpec);
            if (entityMapper == null) {
                return null;
            }

            return ComponentMap.subclassComponent(internalFieldName, externalFieldName, pluginName, isAttribute, entityMapper, access, exclusive);
        }

        boolean flattened = false;
        boolean outerJoin = false;
        Set<TokenType> options = new HashSet<>(Arrays.asList(TokenType.Flattened, TokenType.Optional));
        while (peekToken().is(options)) {
            switch (peekToken().getTokenType()) {
                case Flattened:
                    flattened = true;
                    break;
                case Optional:
                    outerJoin = true;
                    break;
            }
            options.remove(nextToken().getTokenType());
        }

        JoinSpec joinSpec = new JoinSpec();
        EntityFieldMap.Cascade cascade = null;
        switch (peekToken().expect(TokenType.OpenAngleBracket, TokenType.Subclass, TokenType.Identifier).getTokenType()) {
            case OpenAngleBracket: // reflected component map
                nextToken();
                entityClass = getEntityClass(nextToken().expect(TokenType.Identifier).getValue(), pluginName);
                nextToken().expect(TokenType.CloseAngleBracket);

                if (peekToken().is(TokenType.Join)) {
                    joinSpec = parseJoinSpec(entityClass);
                }

                if (peekToken().is(TokenType.With)) {
                    cascade = parseCascadeSpec();
                }

                return ComponentMap.component(internalFieldName, externalFieldName, pluginName, isAttribute,
                        EntityMapper.entity(entityClass).map(ReflectionMap.reflection(entityClass)).build(), access, exclusive, outerJoin, flattened,
                        joinSpec.foreignKey, joinSpec.parentKey, cascade);
            default: // specified component map
                EntitySpec entitySpec = parseEntitySpec(true, pluginName);

                if (peekToken().is(TokenType.Join)) {
                    joinSpec = parseJoinSpec(entitySpec.baseClassSpec.getEntityClass());
                }

                if (peekToken().is(TokenType.With)) {
                    cascade = parseCascadeSpec();
                }

                entityMapper = parseFieldMapList(entitySpec);
                if (entityMapper == null) {
                    return null;
                }
                return ComponentMap.component(internalFieldName, externalFieldName, pluginName, isAttribute, entityMapper, access, exclusive, outerJoin,
                        flattened, joinSpec.foreignKey, joinSpec.parentKey, cascade);
        }
    }

    private boolean isPrimitiveType(Class primitiveType) {
        return primitiveType.equals(String.class) || primitiveType.equals(Integer.class) || primitiveType.equals(Date.class);
    }

    /**
     * Parses an entity spec for a specified entity map or a component map
     *
     * @param allowVariants allow variant classes to be specified?
     * @param pluginName    the name of a plugin upon which the entity is dependent
     * @return an EntitySpec
     */
    private <E> EntitySpec<E> parseEntitySpec(boolean allowVariants, String pluginName) {
        EntitySpec entitySpec = new EntitySpec();
        Token entityClassToken = nextToken().expect(TokenType.Identifier);
        boolean suppressLocalisation = peekToken().is(TokenType.Unlocalised);
        if (suppressLocalisation) {
            nextToken();
        }
        entitySpec.pluginName = pluginName;
        entitySpec.baseClassSpec = new EntityMapper.EntityClassSpec<E>(getEntityClass(entityClassToken.getValue(), pluginName), entityClassToken.getUnresolvedValue(), suppressLocalisation);

        if (allowVariants && peekToken().is(TokenType.OpenParenthesis)) {
            nextToken();
            do {
                entityClassToken = nextToken().expect(TokenType.Identifier);
                entitySpec.subClassSpecs.add(new EntityMapper.EntityClassSpec<E>(getEntityClass(entityClassToken.getValue(), pluginName), entityClassToken.getUnresolvedValue()));

                if (peekToken().is(TokenType.As)) {
                    nextToken();
                    entitySpec.externalDiscriminatorName = nextToken().expect(TokenType.Identifier).getValue();
                    peekToken().expect(TokenType.CloseParenthesis);
                }
            } while (nextToken().expect(TokenType.OrBar, TokenType.CloseParenthesis).is(TokenType.OrBar));
        }
        return entitySpec;
    }

    /**
     * Parses a join spec
     *
     * @return a JoinSpec
     */
    private JoinSpec parseJoinSpec(Class entityClass) {
        JoinSpec joinSpec = new JoinSpec();
        nextToken().expect(TokenType.Join);

        joinSpec.foreignKey = nextToken().expect(TokenType.Identifier).getValue();
        nextToken().expect(TokenType.Reference);
        joinSpec.parentKey = nextToken().expect(TokenType.Identifier).getValue();

        if (peekToken().is(TokenType.Using)) {
            nextToken();

            if (entityClass == null) {
                nextToken().expect(TokenType.Identifier);
            }
        }
        return joinSpec;
    }

    /**
     * Parses a cascade spec
     *
     * @return a JoinSpec
     */
    private EntityFieldMap.Cascade parseCascadeSpec() {
        nextToken().expect(TokenType.With);
        switch (nextToken().expect(TokenType.CascadeUpdate, TokenType.CascadeDelete, TokenType.CascadeAll).getTokenType()) {
            case CascadeUpdate:
                return EntityFieldMap.Cascade.UPDATE;
            case CascadeDelete:
                return EntityFieldMap.Cascade.DELETE;
            case CascadeAll:
                return EntityFieldMap.Cascade.ALL;
        }
        return null;
    }

    private Token nextToken() {
        return nextToken(true);
    }

    /**
     * Parses a field map list
     *
     * @param entitySpec The entity spec to which the field map applies
     * @return an entity mapper containing the field maps, or null if the entity spec contained an invalid handler
     */
    private EntityMapper parseFieldMapList(EntitySpec<?> entitySpec) {
        EntityMapper.MapperBuilder mapperBuilder;
        mapperBuilder = EntityMapper.entity(entitySpec.baseClassSpec);
        for (EntityMapper.EntityClassSpec subClassSpec : entitySpec.subClassSpecs) {
            mapperBuilder.subClass(subClassSpec);
        }
        if (entitySpec.externalDiscriminatorName != null) {
            mapperBuilder.discriminateBy(entitySpec.externalDiscriminatorName);
        }
        nextToken().expect(TokenType.OpenBrace);
        if (peekToken().is(TokenType.CloseBrace)) {
            nextToken();
        } else {
            do {
                FieldMapProvider fieldMapProvider = parseFieldMap(entitySpec);
                if (!entitySpec.invalidHandler && fieldMapProvider != null) {
                    mapperBuilder.map(fieldMapProvider);
                }
            } while (nextToken().expect(TokenType.Comma, TokenType.CloseBrace).is(TokenType.Comma));
        }
        if (entitySpec.invalidHandler) {
            return null;
        }
        return mapperBuilder.build();
    }

    /**
     * Parses a single field map
     *
     * @param entitySpec Details of the entity to which this map relates
     * @return A FieldMapProvider for the parsed map.
     */
    private FieldMapProvider parseFieldMap(EntitySpec<?> entitySpec) {
        if (entitySpec.baseClassSpec == EntityMapper.EntityClassSpec.MERGE) {
            peekToken().expect(TokenType.ReadOnly, TokenType.Final, TokenType.CreateOnly, TokenType.WriteOnly, TokenType.Sensitive, TokenType.Exclusive, TokenType.Identifier);
        } else if (peekToken().expect(TokenType.ReadOnly, TokenType.Final, TokenType.CreateOnly, TokenType.WriteOnly, TokenType.Sensitive, TokenType.Exclusive, TokenType.Identifier, TokenType.OpenParenthesis, TokenType.OpenSquareBracket, TokenType.Attribute, TokenType.Merge, TokenType.Reflection).is(TokenType.Reflection)) {
            nextToken();
            return ReflectionMap.reflection(entitySpec.baseClassSpec.getEntityClass());
        }

        boolean exclusive = false;
        FieldMap.Access access = FieldMap.Access.FULL;

        Set<TokenType> fieldAccessOptions = new HashSet<>(Arrays.asList(TokenType.ReadOnly, TokenType.Final, TokenType.CreateOnly, TokenType.WriteOnly, TokenType.Sensitive));
        Set<TokenType> options = new HashSet<>(fieldAccessOptions);
        options.add(TokenType.Exclusive);
        while (peekToken().is(options)) {
            if (peekToken().is(TokenType.Exclusive)) {
                exclusive = true;
                options.remove(TokenType.Exclusive);
            } else {
                switch (peekToken().getTokenType()) {
                    case ReadOnly:
                        access = FieldMap.Access.READONLY;
                        break;
                    case Final:
                        access = FieldMap.Access.FINAL;
                        break;
                    case CreateOnly:
                        access = FieldMap.Access.CREATEONLY;
                        break;
                    case WriteOnly:
                        access = FieldMap.Access.WRITEONLY;
                        break;
                    case Sensitive:
                        access = FieldMap.Access.SENSITIVE;
                        break;
                }
                options.removeAll(fieldAccessOptions);
            }
            nextToken();
        }

        String internalFieldName = null;
        String pluginName = entitySpec.pluginName;
        boolean isAttribute = false;
        boolean isKey = false;
        boolean isEntityName = false;
        if (peekToken().is(TokenType.OpenParenthesis, TokenType.OpenSquareBracket)) {
            if (peekToken().is(TokenType.OpenParenthesis)) {
                isKey = true;
            } else {
                isEntityName = true;
            }
            nextToken();
            internalFieldName = nextToken().expect(TokenType.Identifier).getValue();
            nextToken().expect(TokenType.CloseParenthesis, TokenType.CloseSquareBracket);
        } else if (peekToken().is(TokenType.Attribute)) {
            if (entitySpec.pluginName != null) {
                throw new EntityMapParserException(String.format("Nested attribute fields not allowed at '%s' %s", input.subSequence(0, Math.min(20, input.length())), source));
            }
            nextToken();
            isAttribute = true;
            pluginName = nextToken().expect(TokenType.Identifier).getValue();
            nextToken().expect(TokenType.Colon).getValue();
            internalFieldName = nextToken().expect(TokenType.Identifier).getValue();
        } else if (peekToken().is(TokenType.Merge)) {
            nextToken();
            peekToken().expect(TokenType.As);
        } else {
            internalFieldName = nextToken().getValue();
        }
        String externalFieldName = internalFieldName;
        if (peekToken().is(TokenType.As)) {
            nextToken();
            externalFieldName = nextToken().expect(TokenType.Identifier).getValue();
        }
        if (entitySpec.baseClassSpec == EntityMapper.EntityClassSpec.MERGE) {
            nextToken().expect(TokenType.Colon);
            peekToken().expect(TokenType.OpenSquareBracket);
            return parseCollectionMap(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive);
        }
        switch (peekToken().getTokenType()) {
            case Colon:
                nextToken();
                if (internalFieldName == null) {
                    peekToken().expect(TokenType.OpenSquareBracket);
                    return parseMergedCollectionMap(externalFieldName, access, exclusive);
                }
                switch (peekToken().expect(TokenType.OpenSquareBracket, TokenType.OpenAngleBracket, TokenType.Subclass, TokenType.Optional, TokenType.Flattened, TokenType.Identifier).getTokenType()) {
                    case OpenSquareBracket:
                        return parseCollectionMap(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive);
                    default:
                        return parseComponentMap(internalFieldName, externalFieldName, pluginName, isAttribute, access, exclusive);
                }
            default:
                Object defaultValue = null;
                if (peekToken().is(TokenType.Equal)) {
                    nextToken();
                    defaultValue = parseLiteral();
                }
                return SimpleFieldMap.fieldMap(internalFieldName, externalFieldName, pluginName, isAttribute, isKey, isEntityName, access, exclusive, defaultValue);
        }
    }

    /**
     * Parses a literal
     *
     * @return a String, Integer, BigDecimal, or Boolean value
     */
    private Object parseLiteral() {
        Token literalToken = nextToken().expect(TokenType.StringLiteral, TokenType.IntegerLiteral, TokenType.DecimalLiteral, TokenType.True, TokenType.False);
        String literal = literalToken.getValue();
        switch (literalToken.getTokenType()) {
            case StringLiteral:
                return literal.substring(1, literal.length() - 1);
            case IntegerLiteral:
                return new Integer(literal);
            case DecimalLiteral:
                return new BigDecimal(literal);
            case True:
                return Boolean.TRUE;
            case False:
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    /**
     * Wraps Class.forName()
     *
     * @param entityClassName the name of the class to obtain
     * @param pluginName      the name of a plugin upon which the entity is dependent
     * @return the class, or null if the specified plugin is not present
     * @throws EntityMapParserException if the class cannot be obtained
     */
    private Class getEntityClass(String entityClassName, String pluginName) {
        if ("String".equalsIgnoreCase(entityClassName)) {
            return String.class;
        } else if ("Integer".equalsIgnoreCase(entityClassName)) {
            return Integer.class;
        } else if ("Date".equalsIgnoreCase(entityClassName)) {
            return Date.class;
        }
        try {
            return Class.forName(entityClassName);
        } catch (ClassNotFoundException e) {
            throw new EntityMapParserException(String.format("Entity class %s not found %s", entityClassName, source), e);
        }
    }

    /**
     * Obtains the next token from the input.
     *
     * @param consume If true, the returned token is consumed. If false, the returned token is retained, and will be returned on the next call to nextToken().
     * @return the next token.
     * @throws EntityMapParserException if an unrecognised token is encountered.
     */
    private Token nextToken(boolean consume) {

        // Return the already parsed token if it was not consumed
        Token token;
        if (_nextToken != null) {
            token = _nextToken;
            if (consume) {
                _nextToken = null;
            }
            return token;
        }

        // Special token for end of input
        if (input.length() == 0) {
            return new Token(TokenType.EndOfInput, "");
        }

        // Comments
        while (input.startsWith("//") || input.startsWith("/*")) {
            String terminator = input.startsWith("//") ? "\n" : "*/";
            int i = input.indexOf(terminator);
            if (i >= 0) {
                input = input.substring(i + terminator.length()).trim();
            }
        }

        // Search for a token match
        for (TokenType tokenType : TokenType.values()) {
            if (tokenType.getPattern() == null) {
                continue;
            }
            Matcher matcher = tokenType.getPattern().matcher(input);
            if (matcher.find()) {
                String tokenValue = matcher.group(1).trim();
                input = matcher.replaceFirst("").trim();
                token = new Token(tokenType, tokenValue);
                if (!consume) {
                    _nextToken = token;
                }
                return token;
            }
        }
        throw new EntityMapParserException(String.format("Unrecognised token at '%s' %s", input.subSequence(0, Math.min(20, input.length())), source));
    }

    private Token peekToken() {
        return nextToken(false);
    }

    /**
     * The valid tokens.
     * Keywords must precede identifiers. More specific patterns must precede less specific patterns.
     * Implements CharSequence to allow String join operations.
     */
    private enum TokenType implements CharSequence {
        OpenBrace("\\{", "open-brace"),
        CloseBrace("\\}", "close-brace"),
        OpenSquareBracket("\\[", "open-square-bracket"),
        CloseSquareBracket("\\]", "close-square-bracket"),
        Reflection("\\<\\>", "reflection-operator"),
        Reference("\\-\\>", "reference-operator"),
        OpenAngleBracket("\\<", "open-angle-bracket"),
        CloseAngleBracket("\\>", "close-angle-bracket"),
        OpenParenthesis("\\(", "open-parenthesis"),
        CloseParenthesis("\\)", "close-parenthesis"),
        Comma("\\,", "comma"),
        Colon("\\:", "colon"),
        Equal("\\=", "colon"),
        Semicolon("\\;", "semicolon"),
        OrBar("\\|", "or-bar"),
        Attribute("\\@", "attribute-indicator"),
        Alias("alias[\\s]+", "alias"),
        As("as[\\s]+", "as"),
        ReadOnly("readonly[\\s]+", "readonly"),
        Final("final[\\s]+", "final"),
        CreateOnly("createonly[\\s]+", "createonly"),
        WriteOnly("writeonly[\\s]+", "writeonly"),
        Sensitive("sensitive[\\s]+", "sensitive"),
        Exclusive("exclusive[\\s]+", "exclusive"),
        Lazy("lazy[\\s]+", "lazy"),
        Eager("eager[\\s]+", "eager"),
        Merge("merge[\\s]+", "merge"),
        Join("join[\\s]+", "join"),
        Indexed("indexed[\\s]+", "indexed"),
        By("by[\\s]+", "by"),
        Optional("optional[\\s]+", "optional"),
        Using("using[\\s]+", "using"),
        Subclass("subclass[\\s]+", "subclass"),
        Flattened("flattened[\\s]+", "flattened"),
        With("with[\\s]+", "with"),
        Unlocalised("unlocalised[\\s]+", "unlocalised"),
        CascadeUpdate("(cascade-update[\\s]*)(\\s|(?=]))", "cascade-update"),
        CascadeDelete("(cascade-delete[\\s]*)(\\s|(?=]))", "cascade-delete"),
        CascadeAll("(cascade-all[\\s]*)(\\s|(?=]))", "cascade-all"),
        True("(true[\\s]*)(\\s|(?=[,}]))", "true"),
        False("(false[\\s]*)(\\s|(?=[,}]))", "false"),
        Identifier("[a-zA-Z_][a-zA-Z0-9._$-]*", "identifier"),
        DecimalLiteral("[0-9]*\\.[0-9]+", "decimal-literal"),
        IntegerLiteral("[0-9]+", "integer-literal"),
        StringLiteral("((\"[^\"]*\")|('[^']*'))", "string-literal"),
        EndOfInput(null, "end of input");

        private Pattern pattern;
        private String description;

        TokenType(String regex, String description) {
            this.pattern = regex == null ? null : Pattern.compile("^(" + regex + ")");
            this.description = description;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public String toString() {
            return description;
        }

        public int length() {
            return description.length();
        }

        public char charAt(int index) {
            return description.charAt(index);
        }

        public CharSequence subSequence(int start, int end) {
            return description.subSequence(start, end);
        }
    }

    private class JoinSpec {
        String foreignKey;
        String parentKey;
    }

    private class EntitySpec<E> {
        String pluginName;
        EntityMapper.EntityClassSpec<E> baseClassSpec;
        List<EntityMapper.EntityClassSpec<E>> subClassSpecs = new ArrayList<>();
        String externalDiscriminatorName;
        boolean invalidHandler;
    }

    /**
     * A token read from the input
     */
    private class Token {
        private final TokenType tokenType;
        private final String value;

        Token(TokenType tokenType, String value) {
            this.tokenType = tokenType;
            this.value = value;
        }

        private TokenType getTokenType() {
            return tokenType;
        }

        private String getValue() {
            if (tokenType == TokenType.Identifier && aliasMap.containsKey(value)) {
                return aliasMap.get(value);
            }
            return value;
        }

        private String getUnresolvedValue() {
            return value;
        }

        /**
         * Checks whether this token is one of the specified types.
         *
         * @param tokenTypes a varargs array of token types
         * @return true if the token type is in the specified array, false otherwise
         */
        private boolean is(TokenType... tokenTypes) {
            return is(new HashSet<>(Arrays.asList(tokenTypes)));
        }

        private boolean is(Set<TokenType> tokenTypes) {
            return tokenTypes.contains(this.tokenType);
        }

        /**
         * Checks whether this token is one of the specified types.
         *
         * @param tokenTypes a varargs array of token types
         * @return this token
         * @throws EntityMapParserException if this token is not one of the expected types
         */
        private Token expect(TokenType... tokenTypes) {
            if (!is(tokenTypes)) {
                throw new EntityMapParserException(String.format("Expecting %s - found '%s' before '%s' %s",
                        String.join(" or ", (CharSequence[]) tokenTypes), value, input.subSequence(0, Math.min(20, input.length())), source));
            }
            return this;
        }
    }
}