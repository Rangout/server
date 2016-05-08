package com.herokuapp.rangout

import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured
import grails.transaction.Transactional

@Transactional(readOnly = true)
@Secured(['isFullyAuthenticated()'])
class UserController {

    static allowedMethods = [getOrders: "GET",  getEstablishmentMenu: "GET",
                             register : "POST", makeOrder: "POST", selectTable: "POST"]

    @Secured(["permitAll"])
    def register() {
        def object = request.JSON
        def user = new User(object['user'])

        user.validate()
        if (user.hasErrors()) {
            def errors = []
            user.errors.fieldErrors?.each { error ->
                errors.add([field: error.field, rejectedValue: error.rejectedValue, message: error.defaultMessage])
            }
            forward(controller: 'error', action: 'badRequest', params: [message: errors])
        }

        user.save(failOnError: true)
        def message = "User " + user.name + " registered with success under socialId: " + user.socialId
        JSON.use('usrSave') {
            render(status: 200, contentType: 'application/json') {[
                    user: user,
                    message: message,
                    status: 'ok'
            ]}
        }
    }

    @Secured(["permitAll"])
    def getEstablishmentMenu() {
        def establishment = Establishment.findById(params?.long('establishmentId', 0))

        if (establishment == null) {
            forward(controller: 'error', action: 'notFound', params: ['message': 'Establishment NOT FOUND'])
        }
        JSON.use('menList') {
            render(status: 200, contentType: 'application/json') {[
                        menu  : establishment?.menu?.findAll { i -> i.available },
                        status: 'ok'
            ]}
        }
    }

    @Secured(["permitAll"])
    def selectTable() {
        def object = request.JSON
        String socialId = object['socialId']

        def user = User.findBySocialId(socialId)
        if (user == null) {
            forward(controller: 'error', action: 'notFound', params: [message: 'User NOT FOUND'])
        }

        def establishment = Establishment.findById(params?.long('establishmentId', 0))
        if (establishment == null) {
            forward(controller: 'error', action: 'notFound', params: [message: 'Establishment NOT FOUND'])
        }

        if (establishment.hasFreeTable()) {
            def table = establishment.getAFreeTable()

            if (user.currentTable == null) {
                def userTable = new UserTable(user: user, table: table)

                user.currentTable = userTable
                user.save(failOnError: true, flush: true)

                table.isFree = false
                table.save(failOnError: true, flush: true)
            }

            JSON.use('selTable') {
                render(status: 200, contentType: 'application/json') {[
                        table: user.currentTable,
                        status: 'ok'
                ]}
            }
        } else {
            return Api.resourceUsed(this, 503, 'Establishment not has a free table, try again later.')
        }
    }

    @Secured(["permitAll"])
    def getOrders() {
        def user = User.findBySocialId((String) params?.getOrDefault('socialId', ''))
        if (user == null) {
            forward(controller: 'error', action: 'notFound', params: [message: 'User NOT FOUND'])
        }

        if (user.currentTable == null) {
            forward(controller: 'error', action: 'badRequest', params: [message: 'Unsupported operation, first select a table of an establishment.'])
        }

        JSON.use('ordList') {
            render(status: 200, contentType: 'application/json') {[
                    orders: Order.findByTableAndClosed(user.currentTable, false),
                    status: 'ok'
            ]}
        }
    }

    @Secured(["permitAll"])
    def makeOrder() {
        def object = request.JSON

        String socialId = object['socialId']

        def user = User.findBySocialId(socialId)
        if (user == null) {
            forward(controller: 'error', action: 'notFound', params: [message: 'User NOT FOUND'])
        }

        if (user.currentTable == null) {
            forward(controller: 'error', action: 'badRequest', params: [message: 'Unsupported operation, first select a table of an establishment.'])
        }

        JSON.use('makOrder') {
            render(status: 200, contentType: 'application/json') {[
                    orders: Order.findByTableAndClosed(user.currentTable, false),
                    status: 'ok'
            ]}
        }
    }
}
