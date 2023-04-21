package de.tum.bgu.msm.run.data.person;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.data.person.Gender;
import de.tum.bgu.msm.data.person.PersonFactory;
import de.tum.bgu.msm.data.person.PersonRole;
import de.tum.bgu.msm.data.person.Occupation;

public class PersonFactoryBkk implements PersonFactory {

    @Override
    public PersonBkk createPerson(int id, int age,
                                  Gender gender, Occupation occupation,
                                  PersonRole role, int workplace,
                                  int income) {
        return new PersonBkk(id, age, gender,
                occupation, role, workplace,
                income);
    }

    @Override
    public Person giveBirth(Person parent, int id, Gender gender) {
        PersonBkk pp = new PersonBkk(id, 0, gender, Occupation.TODDLER, PersonRole.CHILD, 0, 0);
        pp.setNationality(((PersonBkk) parent).getNationality());
        return pp;
    }

    //TODO duplicate as well school attributes
    @Override
    public PersonBkk duplicate(Person originalPerson, int id) {
        PersonBkk duplicate = new PersonBkk(id,
                originalPerson.getAge(),
                originalPerson.getGender(),
                originalPerson.getOccupation(),
                originalPerson.getRole(),
                -1,
                originalPerson.getAnnualIncome());
        duplicate.setDriverLicense(originalPerson.hasDriverLicense());
        duplicate.setNationality(((PersonBkk)originalPerson).getNationality());
        duplicate.setSchoolId(((PersonBkk) originalPerson).getSchoolId());
        return duplicate;
    }
}
