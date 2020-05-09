# Save Legend

Save a new legend.

**URL** : `/api/legend`

**Method** : `POST`

**Data example**

```json
{
  "query": "define\nman sub entity, has name;\nname sub attribute, datatype string;\n\ninsert\n$m isa man, has name \"Brandon\";\n\nmatch\n$m isa man, has name $n;\nget;",
  "queryOptions": {
    "includeAnonymousVariables": false,
    "displayOptions": {
      "entityNamingScheme": "BY_VARIABLE",
      "relationNamingScheme": "BY_VARIABLE",
      "attributeNamingScheme": "BY_VALUE"
    }
  },
  "description": "Example Legend"
}
```

## Success Response

**Code** : `200 OK`

**Content example**

```json
{
    "legendId": "Z7rreGPd8k"
}
```
