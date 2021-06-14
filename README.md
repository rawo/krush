## Krush
![Maven Central](https://img.shields.io/maven-central/v/pl.touk.krush/krush-annotation-processor)
![CircleCI](https://img.shields.io/circleci/build/github/TouK/krush)
[![Sputnik](https://sputnik.ci/conf/badge)](https://sputnik.ci/app#/builds/TouK/krush)

**Krush** is a lightweight persistence layer for Kotlin based on [Exposed SQL DSL](https://github.com/JetBrains/Exposed/wiki/DSL). It’s similar to [Requery](http://requery.io) and [Micronaut-data jdbc](https://micronaut-projects.github.io/micronaut-data/latest/guide/#jdbc), but designed to work idiomatically with Kotlin and immutable data classes.

It’s based on a compile-time JPA annotation processor that generates Exposed DSL table and objects mappings for you. This lets you instantly start writing type-safe SQL queries without need to write boilerplate infrastructure code.

### Rationale 
* **(type-safe) SQL-first** - use type-safe SQL-like DSL in your queries, no string or method name parsing 
* **Minimal changes to your domain model** - no need to extend external interfaces and used special types - just add annotations to your existing domain model
* **Explicit fetching** - you specify explicitly in query what data you want to fetch, no additional fetching after data is loaded
* **No runtime magic** - no proxies, lazy loading, just data classes containing data fetched from DB
* **Pragmatic** - easy to start, but powerful even in not trivial cases (associations, grouping queries)

### Example
Given a simple `Book` class:

```kotlin
data class Book(
   val id: Long? = null,
   val isbn: String,
   val title: String,
   val author: String,
   val publishDate: LocalDate
)
```

we can turn it into **Krush** entity by adding `@Entity` and `@Id` annotations:

```kotlin
@Entity
data class Book(
   @Id @GeneratedValue
   val id: Long? = null,
   val isbn: String,
   val title: String,
   val author: String,
   val publishDate: LocalDate
)
```

When we build the project we’ll have `BookTable` mapping generated for us. So we can persist the `Book`:

```kotlin
val book = Book(
   isbn = "1449373321", publishDate = LocalDate.of(2017, Month.APRIL, 11),
   title = "Designing Data-Intensive Applications", author = "Martin Kleppmann"
)

// insert method is generated by Krush
val persistedBook = BookTable.insert(book)
assertThat(persistedBook.id).isNotNull()
```

So we have now a `Book` persisted in DB with autogenerated `Book.id` field.
And now we can use type-safe SQL DSL to query the `BookTable`:

```kotlin
val bookId = book.id ?: throw IllegalArgumentException()

// toBook method is generated by Krush
val fetchedBook = BookTable.select { BookTable.id eq bookId }.singleOrNull()?.toBook()
assertThat(fetchedBook).isEqualTo(book)

// toBookList method is generated by Krush
val selectedBooks = (BookTable)
   .select { BookTable.author like "Martin K%" }
   .toBookList()

assertThat(selectedBooks).containsOnly(persistedBook)
```

### Installation
Gradle:
```groovy
repositories {
    mavenCentral()
}

apply plugin: 'kotlin-kapt'
api "pl.touk.krush:krush-annotation-processor:$krushVersion"
kapt "pl.touk.krush:krush-annotation-processor:$krushVersion"
api "pl.touk.krush:krush-runtime:$krushVersion"
```

Maven:
```xml
<dependencies>
    <dependency>
        <groupId>pl.touk.krush</groupId>
        <artifactId>krush-runtime</artifactId>
        <version>${krush.version}</version>
    </dependency>
</dependencies>

...

<plugin>
    <groupId>org.jetbrains.kotlin</groupId>
    <artifactId>kotlin-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>kapt</id>
            <goals>
                <goal>kapt</goal>
            </goals>
            <configuration>
                ...
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <groupId>pl.touk.krush</groupId>
                        <artifactId>krush-annotation-processor</artifactId>
                        <version>${krush.version}</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </execution>
        ...
    </executions>
</plugin>
```

### Dependencies
* Exposed 0.3.0 -> 0.24.1, 0.2.0 -> 0.18.1
* JPA annotations 2.1

### Features
* generates table mappings and functions for mapping from/to data classes
* type-safe SQL DSL without reading schema from existing database (code-first)
* explicit association fetching (via `leftJoin` / `innerJoin`)
* multiple data types support, including type aliases
* custom data type support (with `@Converter`), also for wrapped auto-generated ids
* you can still persist associations not directly reflected in domain model (eq. article favorites) 

However, **Krush** is not a full-blown ORM library. This means following JPA features are not supported:
* lazy association fetching
* dirty checking
* caching
* versioning / optimistic locking

### Updating
Given following entity:
```kotlin
@Entity
data class Reservation(
    @Id
    val uid: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    val status: Status = Status.FREE,

    val reservedAt: LocalDateTime? = null,
    val freedAt: LocalDateTime? = null
) {
    fun reserve() = copy(status = Status.RESERVED, reservedAt = LocalDateTime.now())
    fun free() = copy(status = Status.FREE, freedAt = LocalDateTime.now())
}

enum class Status { FREE, RESERVED }
```
you can call Exposed `update` with generated `from` metod to overwrite it's data:

```kotlin
val reservation = Reservation().reserve().let(ReservationTable::insert)

val freedReservation = reservation.free()
ReservationTable.update({ ReservationTable.uid eq reservation.uid }) { it.from(freedReservation) }

val updatedReservation = ReservationTable.select({ ReservationTable.uid eq reservation.uid }).singleOrNull()?.toReservation()
assertThat(updatedReservation?.status).isEqualTo(Status.FREE)
assertThat(updatedReservation?.reservedAt).isEqualTo(reservation.reservedAt)
assertThat(updatedReservation?.freedAt).isEqualTo(freedReservation.freedAt)
```

For simple cases you can still use Exposed native update syntax:
```kotlin
val freedAt = LocalDateTime.now()
ReservationTable.update({ ReservationTable.uid eq reservation.uid }) {
  it[ReservationTable.status] = Status.FREE
  it[ReservationTable.freedAt] = freedAt
}
```
[Complete example](https://github.com/TouK/krush-example/blob/master/src/test/kotlin/pl/touk/krush/ReservationTest.kt)

### Associations

```kotlin
@Entity
@Table(name = "articles")
data class Article(
    @Id @GeneratedValue
    val id: Long? = null,

    @Column(name = "title")
    val title: String,

    @ManyToMany
    @JoinTable(name = "article_tags")
    val tags: List<Tag> = emptyList()
)

@Entity
@Table(name = "tags")
data class Tag(
    @Id @GeneratedValue
    val id: Long? = null,

    @Column(name = "name")
    val name: String
)
```

Persisting

```kotlin
val tag1 = Tag(name = "jvm")
val tag2 = Tag(name = "spring")

val tags = listOf(tag1, tag2).map(TagTable::insert)
val article = Article(title = "Spring for dummies", tags = tags)
val persistedArticle = ArticleTable.insert(article)
```

Querying and fetching
```kotlin
val (selectedArticle) = (ArticleTable leftJoin ArticleTagsTable leftJoin TagTable)
    .select { TagTable.name inList listOf("jvm", "spring") }
    .toArticleList()

assertThat(selectedArticle).isEqualTo(persistedArticle)
```

Update logic for associations not implemented (yet!) - you have to manually add/remove records from `ArticleTagsTable`.

### Example projects

* [https://github.com/TouK/kotlin-exposed-realworld](https://github.com/TouK/kotlin-exposed-realworld)
* [https://github.com/TouK/krush-example](https://github.com/TouK/krush-example)

### Contributors
* [Mateusz Śledź](https://github.com/mateuszsledz)
* [Piotr Jagielski](https://github.com/pjagielski)
* [Namnodorel](https://github.com/Namnodorel)

Special thanks to [Łukasz Jędrzejewski](https://github.com/jedrz) for original idea of using Exposed in our projects.

### Licence
**Krush** is published under Apache License 2.0.
