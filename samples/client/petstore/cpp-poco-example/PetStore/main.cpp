#include <OAIPet.h>
#include <OAIPetApi.h>
#include <OAIStoreApi.h>

#include <Poco/Net/NetException.h>

int main(int argc, char *argv[]){
    std::cout << "Adding pet" << std::endl;

    OpenAPI::OAICategory category;
    category.setId(1);
    category.setName("mamal");

    OpenAPI::OAIPet pet;
    pet.setId(123);
    pet.setName("cat");
    pet.setCategory(category);

    OpenAPI::OAIPetApi petApi;
    petApi.initializeServerConfigs();

    try {
        petApi.addPet(pet);
    }catch (const Poco::Net::HostNotFoundException& ex){
        std::cout << "Nedostupny server" << std::endl;
    }

    std::cout << "Calling for store inventory" << std::endl;
    OpenAPI::OAIStoreApi storeApi;
    storeApi.initializeServerConfigs();

    try {
        storeApi.getInventory();
    }catch (const Poco::Net::HostNotFoundException& ex){
        std::cout << "Nedostupny server" << std::endl;
    }
    return 0;
}


