package pl.touk.exposed.one2many.bidi

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.FetchType
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.OneToMany
import javax.persistence.Table

@Entity
@Table(name = "customers")
data class Customer(
        @Id @GeneratedValue
        val id: Long? = null,

        @Column(name = "name", length = 100)
        val name: String,

        @Column(name = "age")
        val age: Long,

        @OneToMany(fetch = FetchType.EAGER, mappedBy = "customer")
        val phones: List<Phone> = emptyList(),

        @OneToMany(fetch = FetchType.EAGER, mappedBy = "customer")
        val addresses: List<Address> = emptyList()
)

@Entity
@Table(name = "phones")
data class Phone(
        @Id @GeneratedValue
        val id: Long? = null,

        @Column(name = "number")
        val number: String,

        @ManyToOne
        @JoinColumn(name = "customer_id")
        val customer: Customer? = null
)

@Entity
@Table(name = "addresses")
data class Address(
        @Id @GeneratedValue
        val id: Long? = null,

        @Column(name = "city")
        val city: String,

        @Column(name = "street")
        val street: String,

        @Column(name = "house")
        val houseNo: String,

        @Column(name = "apartment")
        val apartmentNo: String,

        @ManyToOne
        @JoinColumn(name = "customer_id")
        val customer: Customer? = null
)
