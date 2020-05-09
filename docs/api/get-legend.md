# Get Legend

Get the saved legend.

**URL** : `/api/legend/{legendId}`

**Method** : `GET`

## Success Response

**Code** : `200 OK`

**Content example**

```json
{
  "id": "rGG0IOQBZs",
  "description": "Example Legend",
  "query": "define\nman sub entity, has name;\nname sub attribute, datatype string;\n\ninsert\n$m isa man, has name \"Brandon\";\n\nmatch\n$m isa man, has name $n;\nget;",
  "queryOptions": {
    "includeAnonymousVariables": false,
    "displayOptions": {
      "entityNamingScheme": "BY_VARIABLE",
      "relationNamingScheme": "BY_VARIABLE",
      "attributeNamingScheme": "BY_VALUE",
      "linkNodesById": false
    }
  }
}
```

## Error Response

**Condition** : Legend does not exist.

**Code** : `404 NOT FOUND`
