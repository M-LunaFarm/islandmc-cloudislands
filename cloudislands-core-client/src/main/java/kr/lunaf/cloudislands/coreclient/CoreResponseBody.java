package kr.lunaf.cloudislands.coreclient;

record CoreResponseBody(String value) {
    CoreResponseBody {
        value = value == null ? "" : value;
    }
}
