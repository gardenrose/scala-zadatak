CREATE TABLE advert (
    id INT PRIMARY KEY,
    title varchar(100) NOT NULL,
    fueltype varchar(50) NOT NULL,
    price int NOT NULL,
    isnew boolean NOT NULL,
    mileage int NOT NULL,
    firstregistration DATE NOT NULL
);