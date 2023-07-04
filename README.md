# entity-mapper

This project has some code examples of mapping entities to interchange between backend and frontend

Main classes are entity.mapper.EntityMapper and entity.mapper.EntityMapperParser

EntityMapperParser - Parse a file in the following structure and create a EntityMapper:

file appidentity.em

alias data.ContactData as AppIdentityData;
alias data.AddressData as AddressData;

AppIdentityData {
    (id),
    email,
    mobilePhone,
    createonly password,
    firstName,
    lastName,
    dob,
    gender,
    deliveryInstructions,
    homeAddress: AddressData {
        street,
        city,
        state,
        postCode,
        country
    }
}

EntityMapper -  This class maps Java Object from/to json. It is them used to map the rest call input and setting the data to the target POJO that will be used to save in the database. Also it is used to read a POJO in Java and convert the result to a json file.
